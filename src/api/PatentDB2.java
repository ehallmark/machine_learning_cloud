package api;

import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static spark.Spark.get;

public class PatentDB2 {
    private enum PatentType {
        grant,
        publication,
        application
    }

    private static final Set<String> ARRAY_FIELDS = Collections.synchronizedSet(new HashSet<>());
    static {
        ARRAY_FIELDS.add("inventors");
    }

    private static final String[] PATENT_FIELDS = new String[]{
            "title",
            "abstract",
            "description",
            "inventors",
            "priority_date",
            "filing_date",
            "publication_date",
            "claims"
    };

    private static final Map<String, String> PRIORITY_DATE_CACHE = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> FAMILY_ID_CACHE = Collections.synchronizedMap(new HashMap<>());

    private static final Map<String, String> FIELD_TO_SUBSTATEMENT_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        FIELD_TO_SUBSTATEMENT_MAP.put("title", "invention_title[array_position(invention_title_lang,'en')] as title");
        FIELD_TO_SUBSTATEMENT_MAP.put("abstract", "abstract[array_position(abstract_lang,'en')] as abstract");
        FIELD_TO_SUBSTATEMENT_MAP.put("description", "description[array_position(description_lang,'en')] as description");
        FIELD_TO_SUBSTATEMENT_MAP.put("inventors", "inventor as inventors");
        FIELD_TO_SUBSTATEMENT_MAP.put("priority_date", "priority_date::text as priority_date");
        FIELD_TO_SUBSTATEMENT_MAP.put("publication_date", "publication_date::text as publication_date");
        FIELD_TO_SUBSTATEMENT_MAP.put("filing_date", "filing_date::text as filing_date");
        FIELD_TO_SUBSTATEMENT_MAP.put("claims", "claims[array_position(claims_lang,'en')] as claims");
    }

    private static final Map<String, Function<Object, Object>> FIELD_TO_POSTPROCESSOR_MAP = Collections.synchronizedMap(new HashMap<>());
    static {
        FIELD_TO_POSTPROCESSOR_MAP.put("claims", claimsText -> ClaimsProcessor.processClaimsString((String)claimsText));
    }

    private static class APIHandler {
        private Connection connection;
        private Lock lock;
        private String connectionString;

        private APIHandler(String connectionString) {
            this.connectionString = connectionString;
            this.lock = new ReentrantLock();
            this.resetConnection();
        }

        private void resetConnection() {
            this.lock.lock();
            try {
                try {
                    this.connection.close();
                } catch (Exception e) {
                    // pass
                }
                this.connection = null;
                this.connection = DriverManager.getConnection(connectionString);
                this.connection.setAutoCommit(true);

            } catch(Exception e) {
                e.printStackTrace();
            } finally {
                this.lock.unlock();
            }

        }

        public List<Map<String, Object>> runQuery(String query, List<String> headers, Consumer<PreparedStatement> statementConsumer, int retries) {
            for (int retry = 0; retry < retries; retry ++) {
                final List<Map<String, Object>> data = new ArrayList<>(10);
                boolean success = true;
                this.lock.lock();
                try {
                    PreparedStatement ps = this.connection.prepareStatement(query);
                    statementConsumer.accept(ps);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        Map<String, Object> item = new HashMap<>(headers.size());
                        for (int i = 0; i < headers.size(); i++) {
                            String header = headers.get(i);
                            if (ARRAY_FIELDS.contains(header)) {
                                Array array = rs.getArray(i + 1);
                                item.put(header, array == null ? new Object[]{} : (Object[])array.getArray());
                            } else {
                                item.put(header, rs.getObject(i + 1));
                            }
                            if (FIELD_TO_POSTPROCESSOR_MAP.containsKey(header)) {
                                System.out.println("Applying postprocessor to field: "+header);
                                item.put(header, FIELD_TO_POSTPROCESSOR_MAP.get(header).apply(item.get(header)));
                            }
                        }
                        data.add(item);
                    }

                    rs.close();
                    ps.close();

                } catch (Exception e) {
                    e.printStackTrace();
                    success = false;
                } finally {
                    this.lock.unlock();
                }
                if (success) {
                    return data;
                } else {
                    System.out.println("Resetting...");
                    this.resetConnection();
                }
            }
            return Collections.emptyList();
        }
    }
    private static final String PATENTDB2_URL = "jdbc:postgresql://localhost/patentdb?user=postgres&password=password&tcpKeepAlive=true";
    private static final APIHandler HANDLER = new APIHandler(PATENTDB2_URL);


    private static String handleRequest(Request req, Response res, PatentType patentType, boolean findEpo) {
        try {
            res.header("Access-Control-Allow-Origin", "*"); // CORS
            res.header("Content-Type", "application/json"); // JSON
            String number = req.queryParams("number");
            if (number.startsWith("RE") && !number.startsWith("RE0")) {
                number = "RE0"+number.substring(2);
            }
            System.out.println("Looking for " + patentType.toString() + ": " + number);
            try {
                final Map<String, String> resolved = resolvePatentNumbers(number, patentType);
                if (resolved.values().stream().filter(x->x!=null).count() <= 1) {
                    throw new RuntimeException("Not found");
                }
                System.out.println("Successfully resolved!");
                System.out.println("Num resolved: "+resolved.size());
                try {
                    if (findEpo) {
                        String resolvedNumber = resolved.getOrDefault("grant_number", resolved.get("publication_number"));
                        final Map<String, Object> data = EPO.getEpoData("US"+resolvedNumber.replace("RE0", "RE"), false);
                        return resultsFormatter(data);

                    } else {
                        final boolean includeEpo = req.queryParamOrDefault("include_epo", "f").toLowerCase().startsWith("t");
                        String resolvedNumber = resolved.getOrDefault("grant_number", resolved.get("publication_number"));
                        String updatedPriorityDate = PRIORITY_DATE_CACHE.get(resolvedNumber);
                        String familyId = FAMILY_ID_CACHE.get(resolvedNumber);
                        String earliestMember = null;
                        if (includeEpo && (updatedPriorityDate == null || familyId == null)) {
                            final Map<String, Object> epoData = EPO.getEpoData("US" + resolvedNumber.replace("RE0", "RE"), true);
                            familyId = (String)epoData.get("family_id");
                            String earliestDate = null;
                            for (Map<String, Object> epoResult : (List<Map<String, Object>>) epoData.getOrDefault("family_members", Collections.emptyList())) {
                                if (epoResult.get("country").equals("US")) {
                                    String date = (String) epoResult.get("date");
                                    String member = "US" + epoResult.get("number") + epoResult.get("kind");
                                    if (earliestDate == null) {
                                        earliestDate = date;
                                        earliestMember = member;
                                    } else {
                                        if (earliestDate.compareTo(date) > 0) {
                                            earliestDate = date;
                                            earliestMember = member;
                                        }
                                    }
                                }
                            }
                        }
                        final boolean includeDescription = req.queryParamOrDefault("include_description", "true").toLowerCase().startsWith("t");
                        final boolean includeClaims = req.queryParamOrDefault("include_claims", "true").toLowerCase().startsWith("t");
                        final Map<String, Object> data = getData(resolved, includeDescription, includeClaims);
                        if (earliestMember != null && data.size() > 0) {
                            updatedPriorityDate = getUpdatedPriorityDateEstimation(earliestMember);
                        }
                        if (updatedPriorityDate != null) {
                            PRIORITY_DATE_CACHE.put(resolvedNumber, updatedPriorityDate);
                            data.put("priority_date", updatedPriorityDate);
                        }
                        if (familyId != null) {
                            FAMILY_ID_CACHE.put(resolvedNumber, familyId);
                            data.put("family_id", familyId);
                        }
                        System.out.println("Found data!");
                        return resultsFormatter(data);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                    res.status(500);
                    return new Gson().toJson(Collections.singletonMap("error", "Unknown server error"));
                }

            } catch (Exception e) {
                e.printStackTrace();
                res.status(404); // not found
                return new Gson().toJson(Collections.singletonMap("error", "Not found"));
            }

        } catch(Exception e) {
            e.printStackTrace();
            res.status(400); // invalid request
            return new Gson().toJson(Collections.singletonMap("error", "Invalid request"));
        }
    }

    public static String resultsFormatter(Map<String, Object> data) {
        data.forEach((k, v) -> {
            System.out.println("K: "+k);
            System.out.println("V: "+new Gson().toJson(v));
        });
        return new Gson().toJson(data);
    }

    public static Map<String, String> resolvePatentNumbers(String number, PatentType patentType) {
        return resolvePatentNumbers(number, patentType, false);
    }

    public static Map<String, String> resolvePatentNumbers(String number, PatentType patentType, boolean alreadyRetried) {
        String publicationNumber = null;
        String publicationNumberFull = null;
        String applicationNumber = null;
        String grantNumber = null;
        String grantNumberFull = null;
        String familyId = null;
        String field = null;
        switch (patentType) {
            case grant: {
                grantNumber = number;
                field = "publication_number";
                break;
            }
            case publication: {
                publicationNumber = number;
                field = "publication_number";
                break;
            }
            case application: {
                applicationNumber = number;
                field = "application_number_formatted";
            }
        }
        List<String> headers = Arrays.asList("publication_number", "publication_number_full", "application_number_formatted", "family_id", "kind_code");
        String query = "select " + String.join(",", headers) + " from big_query_family_id where "+field+"_with_country = 'US'||? limit 2";
        Consumer<PreparedStatement> statementConsumer = ps -> {
            try {
                ps.setString(1, number);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        List<Map<String, Object>> data = HANDLER.runQuery(query, headers, statementConsumer, 3);

        for (Map<String, Object> result : data) {
            String publication_number = (String)result.get("publication_number");
            String application_number_formatted = (String)result.get("application_number_formatted");
            String family_id = (String)result.get("family_id");
            if (familyId == null && family_id != null && !family_id.equals("-1")) {
                familyId = family_id;
            }
            if (application_number_formatted != null) {
                if (applicationNumber == null) {
                    applicationNumber = application_number_formatted;
                }
            }
            if (publication_number.length() > 8) {
                // true publication
                if (publicationNumber == null) {
                    publicationNumber = publication_number;
                }
                if (publicationNumberFull == null) {
                    publicationNumberFull = (String)result.get("publication_number_full");
                }
            } else {
                // really a grant
                if (grantNumber == null) {
                    grantNumber = publication_number;
                }
                if (grantNumberFull == null) {
                    grantNumberFull = (String)result.get("publication_number_full");
                }
            }
        }

        if (applicationNumber == null) {
            throw new RuntimeException("Unable to resolve patent number: "+number);
        }
        if ((grantNumberFull == null || publicationNumberFull == null) && !alreadyRetried) {
            return resolvePatentNumbers(applicationNumber, PatentType.application, true);
        }

        Map<String, String> ret = new HashMap<>();

        if (familyId != null) {
            ret.put("family_id", familyId);
        }
        if (publicationNumber != null) {
            ret.put("publication_number", publicationNumber);
        }
        ret.put("application_number", applicationNumber);
        if (grantNumber != null) {
            ret.put("grant_number", grantNumber);
        }
        if (publicationNumberFull != null) {
            ret.put("publication_number_full", publicationNumberFull);
        }
        if (grantNumberFull != null) {
            ret.put("grant_number_full", grantNumberFull);
        }
        return ret;
    }


    private static String getUpdatedPriorityDateEstimation(String numberFull) {
        String query = "select filing_date::text as filing_date from patents_global where publication_number_full=? limit 1";
        Consumer<PreparedStatement> statementConsumer = ps -> {
            try {
                ps.setString(1, numberFull);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        List<Map<String,Object>> data = HANDLER.runQuery(query, Collections.singletonList("filing_date"), statementConsumer, 3);
        if (data.size() > 0) {
            String priorityDate = (String)data.get(0).get("filing_date");
            System.out.println("Found updated priority date: "+priorityDate);
            return priorityDate;
        } else {
            return null;
        }
    }

    public static Map<String, Object> getData(Map<String, String> resolved, boolean includeDescription, boolean includeClaims) {
        String publicationNumber = resolved.get("publication_number");
        String applicationNumber = resolved.get("application_number");
        String grantNumber = resolved.get("grant_number");
        String publicationNumberFull = resolved.get("publication_number_full");
        String grantNumberFull = resolved.get("grant_number_full");

        List<String> headers = new ArrayList<>(Arrays.asList(PATENT_FIELDS));
        if (!includeClaims) {
            headers.remove("claims");
        }
        if (!includeDescription) {
            headers.remove("description");
        }

        List<String> statements = headers.stream().map(statement->FIELD_TO_SUBSTATEMENT_MAP.getOrDefault(statement, statement)).collect(Collectors.toList());

        String number;
        String publicationDate = null;
        String priorityDateEst = null;

        if (grantNumberFull != null) {
            // get patent grant data
            // find publication date from publication number
            number = grantNumberFull;
            String pubDateQuery = "select publication_date::text as publication_date from patents_global where publication_number_full = ? limit 1";
            Consumer<PreparedStatement> statementConsumer = ps -> {
                try {
                    ps.setString(1, number);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            //priorityDateEst = getUpdatedPriorityDateEstimation(grantNumberFull);
            List<Map<String,Object>> data = HANDLER.runQuery(pubDateQuery, Collections.singletonList("publication_date"), statementConsumer, 3);
            if (data.size() > 0) {
                publicationDate = (String)data.get(0).get("publication_date");
            } else {
                throw new RuntimeException("Unable to find publication for grant: "+grantNumberFull);
            }
        } else if (publicationNumberFull != null) {
            // get publication data only
            number = publicationNumberFull;
            //priorityDateEst = getUpdatedPriorityDateEstimation(publicationNumberFull);

        } else {
            throw new RuntimeException("Invalid state. Resolved: "+new Gson().toJson(resolved));
        }

        String query = "select "+String.join(",", statements)+" from patents_global where publication_number_full = ? limit 1";

        Consumer<PreparedStatement> statementConsumer = ps -> {
            try {
                ps.setString(1, number);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };
        List<Map<String,Object>> data = HANDLER.runQuery(query, headers, statementConsumer, 3);
        if (data.size() > 0) {
            Map<String, Object> ret = data.get(0);
            ret.put("grant_number", grantNumber);
            ret.put("publication_number", publicationNumber);
            if (publicationDate != null) { // was a grant
                ret.put("issue_date", ret.get("publication_date"));
                ret.put("publication_date", publicationDate);
            }
            if (priorityDateEst != null) {
                ret.put("priority_date", priorityDateEst);
            }
            ret.put("application_number", applicationNumber);
            return ret;
        } else {
            return Collections.emptyMap();
        }
    }



    public static void main(String[] args) {
        get("/v1/api/find_by_grant", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.grant, false));
        get("/v1/api/find_by_publication", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.publication, false));
        get("/v1/api/find_by_application", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.application, false));
        get("/v1/api/family_members_for_grant", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.grant, true));
        get("/v1/api/family_members_for_publication", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.publication, true));
        get("/v1/api/family_members_for_application", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.application, true));
    }
}
