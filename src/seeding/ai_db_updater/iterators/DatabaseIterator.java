package seeding.ai_db_updater.iterators;

/**
 * Created by ehallmark on 1/3/17.
 */

import com.google.gson.Gson;
import elasticsearch.DataIngester;
import lombok.Setter;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import seeding.google.elasticsearch.Attributes;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.portfolios.PortfolioList;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**

 */
public class DatabaseIterator {
    private static boolean debug = false;
    @Setter
    protected static Collection<ComputableAttribute> computableAttributes;
    private LocalDate startDate;
    private LocalDate endDate;
    private Collection<AbstractAttribute> topLevelAttributes;
    public DatabaseIterator(LocalDate startDate, LocalDate endDate) {
        this.endDate=endDate;
        this.startDate=startDate;
    }

    protected void init() {
        this.topLevelAttributes = SimilarPatentServer.getAllTopLevelAttributes();
    }

    public void save() {
        DataIngester.finishCurrentMongoBatch();
        if (computableAttributes != null) {
            computableAttributes.forEach(attr -> {
                attr.save();
            });
        }
    }

    public void run(boolean patentGrantOnly, boolean runAggOnly, boolean runCitationsOnly) throws SQLException {
        Database.setupSeedConn();
        this.init();

        System.out.println("Attrs: "+String.join(", ",topLevelAttributes.stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr.getName())).map(attr->attr.getName()).collect(Collectors.toList())));

        EndFlag claimEndFlag = new EndFlag("") {
            @Override
            public void save() {
                dataMap = new HashMap<>();
            }
        };

        Map<String,Function<Flag,Function<String,?>>> transformationFunctionMap = Collections.synchronizedMap(new HashMap<>());
        transformationFunctionMap.put(Constants.FILING_NAME, Flag.filingDocumentHandler);
        transformationFunctionMap.put(Constants.NAME,Flag.unknownDocumentHandler);
        transformationFunctionMap.put(Constants.PUBLICATION_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.FILING_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.ASSIGNEE,Flag.assigneeTransformationFunction);
        transformationFunctionMap.put(Constants.CITED_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Attributes.CITATIONS+"."+ Attributes.PUBLICATION_NUMBER,Flag.unknownDocumentHandler);
        transformationFunctionMap.put(Constants.CITATION_CATEGORY, f->text->{
            if(text==null || text.trim().length() == 0) return null;
            return text.trim().toLowerCase();
        });
        transformationFunctionMap.put(Constants.PRIORITY_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.PARENT_CLAIM_NUM,f->text->{
            if(text == null || text.trim().length() < 5 ) return null;
            try {
                return Integer.valueOf(text.trim().substring(4,Math.min(text.trim().length(),9)));
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
        });

        if(runAggOnly) {
            runAggregateClaimData();
        } else if(runCitationsOnly) {
            try {
                runNestedTable("patent_grant_citation", null, new CitationsNestedAttribute(), transformationFunctionMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            runPatentGrant(transformationFunctionMap);

            if (!patentGrantOnly) {
                AtomicBoolean errors = new AtomicBoolean(false);
                // nested
                ForkJoinPool pool = new ForkJoinPool(10);
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runPriorityClaim(transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runAggregateClaimData();
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_related_doc", Constants.NAME, new RelatedDocumentsNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_agent", Constants.LAST_NAME, new AgentsNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_applicant", Constants.LAST_NAME, new ApplicantsNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_assignee", Constants.ASSIGNEE, new LatestAssigneeNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });

                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_assignee", null, new AssigneesNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_citation", null, new CitationsNestedAttribute(), transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });
                pool.execute(new RecursiveAction() {
                    @Override
                    protected void compute() {
                        try {
                            runNestedTable("patent_grant_claim", Constants.CLAIM, new ClaimsNestedAttribute(), claimEndFlag, transformationFunctionMap);
                        } catch (Exception e) {
                            errors.set(true);
                            e.printStackTrace();
                        }
                    }
                });


                try {
                    pool.shutdown();
                    pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MICROSECONDS);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (errors.get()) {
                    System.out.println("Errors occured...");
                    System.exit(1);
                }
            }
        }

    }

    private void runPatentGrant(Map<String,Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException{
        Map<String,String> pgNamesToAttrNames = Constants.PG_PATENT_GRANT_ATTRIBUTES.stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr)).collect(Collectors.toMap(attr->Constants.PG_NAME_MAP.get(attr),attr->attr));
        List<String> pgNames = new ArrayList<>(pgNamesToAttrNames.keySet());
        List<String> javaNames = pgNames.stream().map(pg->pgNamesToAttrNames.get(pg)).collect(Collectors.toList());

        List<Function<Flag,Function<String,?>>> transformationFunctions = javaNames.stream().map(attr->{
            return transformationFunctionMap.getOrDefault(attr, Flag.defaultTransformationFunction);
        }).collect(Collectors.toList());

        StringJoiner selectJoin = new StringJoiner(",","select ", " from ");
        pgNames.forEach(name->selectJoin.add(name));

        String patentDBQuery = selectJoin.toString() + " patent_grant where pub_date >= ? and pub_date < ?";
        PreparedStatement patentDBStatement = Database.seedConn.prepareStatement(patentDBQuery);
        patentDBStatement.setInt(1, Integer.valueOf(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        patentDBStatement.setInt(2, Integer.valueOf(endDate.format(DateTimeFormatter.BASIC_ISO_DATE)));

        patentDBStatement.setFetchSize(10);

        System.out.println("Executing query: "+patentDBStatement.toString());

        ResultSet rs = patentDBStatement.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()) {
            Map<String,Object> data = new HashMap<>();
            IntStream.range(0,pgNames.size()).forEach(i->{
                try {
                    Object value = rs.getObject(i + 1);
                    if(value!=null) {
                        String name = javaNames.get(i);
                        Flag flag = Flag.simpleFlag(name,name,null).withTransformationFunction(transformationFunctions.get(i));
                        Object cleanValue = flag.apply(value.toString());
                        data.put(javaNames.get(i), cleanValue);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
            Object patent = data.get(Constants.NAME);
            Object filing = data.get(Constants.FILING_NAME);
            if(patent!=null&&filing!=null) {
                // computable attrs
                if(cnt.getAndIncrement()%100000==99999) {
                    System.out.println("Completed: "+cnt.get());
                }
                computableAttributes.forEach(computableAttribute -> {
                    computableAttribute.handlePatentData(patent.toString(),data);
                });
                data.put(Constants.DOC_TYPE, PortfolioList.Type.patents.toString()); // previous bug
                DataIngester.ingestBulk(patent.toString(), data, true);
            }
        }

        patentDBStatement.close();
    }

    private void runPriorityClaim(Map<String,Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException{
        List<String> javaNames = Arrays.asList(Constants.PRIORITY_DATE);

        String patentDBQuery = "select min(claim_date::integer), c.pub_doc_number from patent_grant_priority_claim as c join patent_grant as p on (c.pub_doc_number=p.pub_doc_number) where claim_date is not null and isnumeric(claim_date) and pub_date >= ? and pub_date < ? group by c.pub_doc_number";
        PreparedStatement patentDBStatement = Database.newSeedConn().prepareStatement(patentDBQuery);
        patentDBStatement.setInt(1, Integer.valueOf(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        patentDBStatement.setInt(2, Integer.valueOf(endDate.format(DateTimeFormatter.BASIC_ISO_DATE)));

        List<Function<Flag,Function<String,?>>> transformationFunctions = javaNames.stream().map(attr->{
            return transformationFunctionMap.getOrDefault(attr, Flag.defaultTransformationFunction);
        }).collect(Collectors.toList());

        patentDBStatement.setFetchSize(10);
        System.out.println("Executing query: "+patentDBStatement.toString());

        ResultSet rs = patentDBStatement.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()) {
            Map<String,Object> data = new HashMap<>();
            IntStream.range(0,javaNames.size()).forEach(i->{
                try {
                    Object value = rs.getObject(i + 1);
                    if(value!=null) {
                        String name = javaNames.get(i);
                        Flag flag = Flag.simpleFlag(name,name,null).withTransformationFunction(transformationFunctions.get(i));
                        Object cleanValue = flag.apply(value.toString());

                        data.put(javaNames.get(i), cleanValue);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
            String patent = rs.getString(javaNames.size()+1);
            if(patent!=null) {
                // computable attrs
                if(cnt.getAndIncrement()%100000==99999) {
                    System.out.println("Completed: "+cnt.get());
                }
                computableAttributes.forEach(computableAttribute -> {
                    computableAttribute.handlePatentData(patent,data);
                });
                DataIngester.ingestBulk(patent, data, false);
            }
        }

        patentDBStatement.close();
    }

    private void runAggregateClaimData() throws SQLException{
        List<String> javaNames = Arrays.asList(Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH,Constants.MEANS_PRESENT);

        String patentDBQuery = "select min(word_count) as "+Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH+", bool_and(means_present) as "+Constants.MEANS_PRESENT+", c.pub_doc_number from patent_grant_claim as c join patent_grant as p on (c.pub_doc_number=p.pub_doc_number) where word_count is not null and parent_claim_id is null and means_present is not null and pub_date >= ? and pub_date < ? group by c.pub_doc_number";
        PreparedStatement patentDBStatement = Database.newSeedConn().prepareStatement(patentDBQuery);
        patentDBStatement.setInt(1, Integer.valueOf(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        patentDBStatement.setInt(2, Integer.valueOf(endDate.format(DateTimeFormatter.BASIC_ISO_DATE)));

        patentDBStatement.setFetchSize(10);
        System.out.println("Executing query: "+patentDBStatement.toString());

        ResultSet rs = patentDBStatement.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()) {
            Map<String,Object> data = new HashMap<>();
            IntStream.range(0,javaNames.size()).forEach(i->{
                try {
                    Object value = rs.getObject(i + 1);
                    if(value!=null) {
                        String name = javaNames.get(i);
                        data.put(name, value);
                    }
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
            String patent = rs.getString(javaNames.size()+1);
            if(patent!=null) {
                // computable attrs
                if(cnt.getAndIncrement()%100000==99999) {
                    System.out.println("Completed: "+cnt.get());
                    System.out.println("Sample "+patent+": "+new Gson().toJson(data));
                }
                DataIngester.ingestBulk(patent, data, false);
            }
        }

        patentDBStatement.close();
    }


    private void runNestedTable(String nestedTableName, String requiredAttr, NestedAttribute nestedAttribute, Map<String, Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException {
        EndFlag endFlag = new EndFlag("") {
            @Override
            public void save() {
                dataMap = new HashMap<>();
            }
        };
        this.runNestedTable(nestedTableName, requiredAttr, nestedAttribute, endFlag, transformationFunctionMap);
    }

    private void runNestedTable(String nestedTableName, String requiredAttr, NestedAttribute nestedAttribute, EndFlag endFlag, Map<String, Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException {
        Map<String,String> pgNamesToAttrNames = nestedAttribute.getAttributes().stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr.getFullName())||Constants.PG_NAME_MAP.containsKey(attr.getName())).collect(Collectors.toMap(attr->Constants.PG_NAME_MAP.getOrDefault(attr.getFullName(),Constants.PG_NAME_MAP.get(attr.getName())),attr->attr.getName()));
        List<String> pgNames = new ArrayList<>(pgNamesToAttrNames.keySet());
        List<String> javaNames = pgNames.stream().map(pg->pgNamesToAttrNames.get(pg)).collect(Collectors.toList());
        nestedAttribute.getAttributes().forEach(attr->endFlag.addChild(Flag.simpleFlag(attr.getName(),attr.getName(),endFlag).withTransformationFunction(transformationFunctionMap.getOrDefault(attr.getFullName(), transformationFunctionMap.getOrDefault(attr.getName(), Flag.defaultTransformationFunction)))));

        StringJoiner selectJoin = new StringJoiner("), array_agg(n.","select array_agg(n.", ")");
        pgNames.forEach(name->selectJoin.add(name));
        String patentDBQuery = selectJoin.toString() + ", n.pub_doc_number from "+nestedTableName+" as n join patent_grant as p on (p.pub_doc_number=n.pub_doc_number) and pub_date >= ? and pub_date < ? group by n.pub_doc_number";
        PreparedStatement patentDBStatement = Database.newSeedConn().prepareStatement(patentDBQuery);
        patentDBStatement.setInt(1, Integer.valueOf(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        patentDBStatement.setInt(2, Integer.valueOf(endDate.format(DateTimeFormatter.BASIC_ISO_DATE)));

        patentDBStatement.setFetchSize(10);

        System.out.println("Executing query: "+patentDBStatement.toString());

        Map<String,AtomicLong> validCountMap = new HashMap<>();

        ResultSet rs = patentDBStatement.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        System.out.println("starting");
        while(rs.next()) {
            List<Object[]> values = new ArrayList<>();
            int maxNumValues = 0;
            for(int i = 0; i < pgNames.size(); i++) {
                Object[] value = (Object[])rs.getArray(i + 1).getArray();
                if(debug) {
                    System.out.println(pgNames.get(i)+" -> "+javaNames.get(i)+": "+(value==null?null:Arrays.toString(value)));
                }
                if(value!=null&&value.length>0) {
                    values.add(value);
                    maxNumValues = Math.max(maxNumValues,value.length);
                } else {
                    values.add(new Object[]{});
                }
            }

            List<Map<String,Object>> dataList = new ArrayList<>();
            for(int v = 0; v < maxNumValues; v++) {
                Map<String,Object> data = new HashMap<>();
                for (int i = 0; i < javaNames.size(); i++) {
                    String name = javaNames.get(i);
                    Object[] valuesForAttr = values.get(i);
                    Object value = v >= valuesForAttr.length ? "" : valuesForAttr[v];
                    Object cleanValue = null;
                    if(value!=null) {
                        Flag flag = endFlag.flagMap.get(name);
                        cleanValue = flag.apply(value.toString());
                    }
                    if(cleanValue!=null) {
                        validCountMap.putIfAbsent(name,new AtomicLong(0));
                        validCountMap.get(name).getAndIncrement();
                        data.put(name, cleanValue);
                    }
                }
                if(requiredAttr==null||data.containsKey(requiredAttr)) {
                    dataList.add(data);
                }
            }

            String patent = rs.getString(pgNames.size()+1);
            if(patent!=null) {
                // computable attrs
                if(cnt.getAndIncrement()%100000==99999) {
                    System.out.println("Completed: "+cnt.get());
                }
                Map<String, Object> fullMap = new HashMap<>();
                if(!dataList.isEmpty()) {
                    if(nestedAttribute.isObject()) {
                        fullMap.put(nestedAttribute.getName(), dataList.get(0));
                    } else {
                        fullMap.put(nestedAttribute.getName(), dataList);
                    }
                }
                endFlag.getDataMap().forEach((flag,attr)->{
                    fullMap.put(flag.dbName,attr);
                });

                if(fullMap.containsKey(Constants.LATEST_ASSIGNEE)) { // handle first name data
                    Map<String, Object> latestAssigneeData = (Map<String,Object>)fullMap.get(Constants.LATEST_ASSIGNEE);
                    Object assigneeName = latestAssigneeData.get(Constants.ASSIGNEE);
                    boolean isHuman = false;
                    if(assigneeName==null) {
                        Object firstName = latestAssigneeData.get(Constants.FIRST_NAME);
                        Object lastName = latestAssigneeData.get(Constants.LAST_NAME);
                        if(firstName!=null&&lastName!=null) {
                            assigneeName = lastName.toString().trim()+", "+firstName.toString().trim();
                            if(assigneeName.toString().length()<4) assigneeName = null;
                        }
                        if(assigneeName!=null) {
                            isHuman = true;
                            latestAssigneeData.put(Constants.ASSIGNEE,assigneeName);
                        }
                    }
                    if(assigneeName!=null) {
                        latestAssigneeData.put(Constants.IS_HUMAN,isHuman);
                    }
                }

                if (debug) System.out.println("Pre Data: " + new Gson().toJson(fullMap));
                if(!fullMap.isEmpty()) {
                    computableAttributes.forEach(computableAttribute -> {
                        computableAttribute.handlePatentData(patent, fullMap);
                    });
                    if (debug) System.out.println("Post Data: " + new Gson().toJson(fullMap));
                    if (!fullMap.isEmpty()) {
                        DataIngester.ingestBulk(patent, fullMap, false);
                    }
                }
            }
            endFlag.save();
        }

        validCountMap.forEach((name,num)->{
            System.out.println("Valid Count for "+name+": "+num.get());
        });
        endFlag.flagMap.clear();
        patentDBStatement.close();
    }

}