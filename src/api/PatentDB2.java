package api;

import com.google.gson.Gson;
import spark.Request;
import spark.Response;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static spark.Spark.get;

public class PatentDB2 {
    private enum PatentType {
        grant,
        publication,
        application
    }

    private static final String[] PATENT_FIELDS = new String[]{
            "title",
            "abstract",
            "description",
            "inventors",
            "priority_date",
            "filing_date",
            "publication_date",
            "issue_date",
            "claims"
    };

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
                            item.put(header, rs.getObject(i + 1));
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
    private static final String PATENTDB2_URL = "jdbc:postgresql://localhost/patentdb?user=ehallmark&password=evan1122=&tcpKeepAlive=true";

    private static final APIHandler HANDLER = new APIHandler(PATENTDB2_URL);


    private static String handleRequest(Request req, Response res, PatentType patentType) {
        try {
            final String number = req.queryParams("number");
            final boolean includeDescription = req.queryParamOrDefault("include_description", "true").toLowerCase().startsWith("t");
            final boolean includeClaims = req.queryParamOrDefault("include_claims", "true").toLowerCase().startsWith("t");
            System.out.println("Looking for " + patentType.toString() + ": " + number);
            try {
                final Map<String, String> resolved = resolvePatentNumbers(number, patentType);
                try {
                    final Map<String, Object> data = getData(resolved, includeDescription, includeClaims);
                    return resultsFormatter(data);
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

        ret.put("family_id", familyId);
        ret.put("publication_number", publicationNumber);
        ret.put("application_number", applicationNumber);
        ret.put("grant_number", grantNumber);
        ret.put("publication_number_full", publicationNumberFull);
        ret.put("grant_number_full", grantNumberFull);

        return ret;
    }

    public static Map<String, Object> getData(Map<String, String> resolved, boolean includeDescription, boolean includeClaims) {
        String familyId = resolved.get("family_id");
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

        String patentType;
        String number;
        String publicationDate = null;

        if (grantNumberFull != null) {
            // get patent grant data
            // find publication date from publication number
            number = grantNumberFull;
            String pubDateQuery = "select publication_date from patents_global where publication_number_full = ? limit 1";
            Consumer<PreparedStatement> statementConsumer = ps -> {
                try {
                    ps.setString(1, number);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            List<Map<String,Object>> data = HANDLER.runQuery(pubDateQuery, headers, statementConsumer, 3);
            if (data.size() > 0) {
                publicationDate = (String)data.get(0).get("publication_date");
            } else {
                throw new RuntimeException("Unable to find publication for grant: "+grantNumberFull);
            }
        } else if (publicationNumberFull != null) {
            // get publication data only
            number = publicationNumberFull;

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
            ret.put("application_number", applicationNumber);
            return ret;
        } else {
            return Collections.emptyMap();
        }
    }



    public static void main(String[] args) {
        get("/v1/api/find_by_grant", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.grant));
        get("/v1/api/find_by_publication", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.publication));
        get("/v1/api/find_by_application", (req, res) -> PatentDB2.handleRequest(req, res, PatentType.application));
    }
}
