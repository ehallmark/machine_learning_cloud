package seeding.ai_db_updater.iterators;

/**
 * Created by ehallmark on 1/3/17.
 */

import elasticsearch.DataIngester;
import lombok.Setter;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.CustomHandler;
import seeding.ai_db_updater.handlers.NestedHandler;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.*;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.portfolios.PortfolioList;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**

 */
public class DatabaseIterator {
    private static boolean debug = false;
    @Setter
    protected static Map<String,INDArray> lookupTable;
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

    public void run() throws SQLException {
        Database.setupSeedConn();
        this.init();

        System.out.println("Attrs: "+String.join(", ",topLevelAttributes.stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr.getName())).map(attr->attr.getName()).collect(Collectors.toList())));


        EndFlag endFlag = new EndFlag("") {
            @Override
            public void save() {
                dataMap = new HashMap<>();
            }
        };

        Map<String,Function<Flag,Function<String,?>>> transformationFunctionMap = Collections.synchronizedMap(new HashMap<>());
        transformationFunctionMap.put(Constants.FILING_NAME, Flag.filingDocumentHandler);
        transformationFunctionMap.put(Constants.PUBLICATION_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.FILING_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.ASSIGNEE,Flag.assigneeTransformationFunction);
        transformationFunctionMap.put(Constants.SMALLEST_INDEPENDENT_CLAIM_LENGTH,Flag.smallestIndClaimTransformationFunction(endFlag));
        transformationFunctionMap.put(Constants.CITED_DATE,Flag.dateTransformationFunction(DateTimeFormatter.BASIC_ISO_DATE));
        transformationFunctionMap.put(Constants.CITATIONS+"."+Constants.NAME,Flag.unknownDocumentHandler);
        transformationFunctionMap.put(Constants.PARENT_CLAIM_NUM,f->text->{
            if(text == null || text.length() < 5 ) return null;
            try {
                return Integer.valueOf(text.substring(4));
            } catch(Exception e) {
                e.printStackTrace();
                return null;
            }
        });


       // runPatentGrant(transformationFunctionMap);

        // nested
        runNestedTable("patent_grant_claim", Constants.CLAIM, new ClaimsNestedAttribute(), Arrays.asList(new LengthOfSmallestIndependentClaimAttribute(), new MeansPresentAttribute()), endFlag, transformationFunctionMap);
        runNestedTable("patent_grant_citation", Constants.NAME, new CitationsNestedAttribute(), Collections.emptyList(), endFlag, transformationFunctionMap);
        runNestedTable("patent_grant_assignee", null, new AssigneesNestedAttribute(), Collections.emptyList(), endFlag, transformationFunctionMap);
        runNestedTable("patent_grant_assignee", Constants.ASSIGNEE, new LatestAssigneeNestedAttribute(), Collections.emptyList(), endFlag, transformationFunctionMap);


    }

    private void runPatentGrant(Map<String,Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException{
        Map<String,String> pgNamesToAttrNames = Constants.PG_PATENT_GRANT_ATTRIBUTES.stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr)).collect(Collectors.toMap(attr->Constants.PG_NAME_MAP.get(attr),attr->attr));
        List<String> pgNames = new ArrayList<>(pgNamesToAttrNames.keySet());
        List<String> javaNames = pgNames.stream().map(pg->pgNamesToAttrNames.get(pg)).collect(Collectors.toList());

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
                        Flag flag = Flag.simpleFlag(name,name,null).withTransformationFunction(transformationFunctionMap.getOrDefault(name,Flag.defaultTransformationFunction));
                        Object cleanValue = flag.apply(value.toString());
                        if (debug) {
                            System.out.println("Value type of " + pgNames.get(i) + ": " + (value.getClass().getName()));
                        }
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
                DataIngester.ingestBulk(patent.toString(), filing.toString(), data, true);
            }
        }

        patentDBStatement.close();
    }

    private void runNestedTable(String nestedTableName, String requiredAttr, NestedAttribute nestedAttribute, Collection<AbstractAttribute> otherAttributes, EndFlag endFlag, Map<String, Function<Flag,Function<String,?>>> transformationFunctionMap) throws SQLException {
        Map<String,String> pgNamesToAttrNames = nestedAttribute.getAttributes().stream().filter(attr->Constants.PG_NAME_MAP.containsKey(attr.getFullName())||Constants.PG_NAME_MAP.containsKey(attr.getName())).collect(Collectors.toMap(attr->Constants.PG_NAME_MAP.getOrDefault(attr.getFullName(),Constants.PG_NAME_MAP.get(attr.getName())),attr->attr.getName()));
        List<String> pgNames = new ArrayList<>(pgNamesToAttrNames.keySet());
        List<String> javaNames = pgNames.stream().map(pg->pgNamesToAttrNames.get(pg)).collect(Collectors.toList());
        javaNames.addAll(otherAttributes.stream().map(attr->attr.getName()).collect(Collectors.toList()));
        Stream.of(nestedAttribute.getAttributes().stream(),otherAttributes.stream()).flatMap(str->str).forEach(attr->endFlag.addChild(Flag.simpleFlag(attr.getName(),attr.getName(),endFlag).withTransformationFunction(transformationFunctionMap.getOrDefault(attr.getFullName(), transformationFunctionMap.getOrDefault(attr.getName(), Flag.defaultTransformationFunction)))));

        StringJoiner selectJoin = new StringJoiner("), array_agg(n.","select array_agg(n.", ")");
        pgNames.forEach(name->selectJoin.add(name));
        String patentDBQuery = selectJoin.toString() + ", n.pub_doc_number from "+nestedTableName+" as n join patent_grant as p on (p.pub_doc_number=n.pub_doc_number) and pub_date >= ? and pub_date < ? group by n.pub_doc_number";
        PreparedStatement patentDBStatement = Database.seedConn.prepareStatement(patentDBQuery);
        patentDBStatement.setInt(1, Integer.valueOf(startDate.format(DateTimeFormatter.BASIC_ISO_DATE)));
        patentDBStatement.setInt(2, Integer.valueOf(endDate.format(DateTimeFormatter.BASIC_ISO_DATE)));

        patentDBStatement.setFetchSize(10);

        System.out.println("Executing query: "+patentDBStatement.toString());

        ResultSet rs = patentDBStatement.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        System.out.println("starting");
        while(rs.next()) {
            List<Object[]> values = new ArrayList<>();
            int numValues = 0;
            for(int i = 0; i < pgNames.size(); i++) {
                Object[] value = (Object[])rs.getArray(i + 1).getArray();
                if(value!=null) {
                    values.add(value);
                    numValues = value.length;
                }
            }

            List<Map<String,Object>> dataList = new ArrayList<>();
            for(int v = 0; v < numValues; v++) {
                Map<String,Object> data = new HashMap<>();
                for (int i = 0; i < javaNames.size(); i++) {
                    String name = javaNames.get(i);
                    Object value = i >= values.size() ? "" : values.get(i)[v];
                    Object cleanValue = null;
                    if(value!=null) {
                        Flag flag = endFlag.flagMap.get(name);
                        cleanValue = flag.apply(value.toString());
                    }
                    if(cleanValue!=null) {
                        data.put(javaNames.get(i), cleanValue);
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
                fullMap.put(nestedAttribute.getName(), dataList);
                endFlag.getDataMap().forEach((flag,attr)->{
                    fullMap.put(flag.dbName,attr);
                });
                computableAttributes.forEach(computableAttribute -> {
                    computableAttribute.handlePatentData(patent.toString(), fullMap);
                });
                DataIngester.ingestBulk(patent, null, fullMap, false);
            }
            endFlag.save();
        }
        endFlag.flagMap.clear();
        patentDBStatement.close();
    }

}