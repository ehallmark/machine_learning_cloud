package seeding.google.elasticsearch;

import elasticsearch.DataSearcher;
import elasticsearch.MyClient;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import seeding.Database;
import seeding.google.elasticsearch.attributes.ConvenienceAttribute;
import seeding.google.elasticsearch.attributes.SimilarityAttribute;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.server.BigQueryServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.portfolios.items.Item;

import java.sql.*;
import java.sql.Date;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IngestESFromPostgres {

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getConn();

        TransportClient client = MyClient.get();
        SearchResponse response = client.prepareSearch(IngestPatents.INDEX_NAME)
                .setTypes(IngestPatents.TYPE_NAME)
                .setFetchSource(false)
                .setSize(10000)
                .setQuery(QueryBuilders.matchAllQuery()).get();
        System.out.println("Starting to find existing ids...");
        final List<String> ids = Collections.synchronizedList(new ArrayList<>(1000));
        AtomicLong exCnt = new AtomicLong(0);
        Function<SearchHit,Item> hitTransformer = hit -> {
            ids.add(hit.getId());
            if(exCnt.getAndIncrement()%10000==9999) {
                System.out.println("Found exclusions: "+exCnt.get());
            }
            return null;
        };

        System.out.println("Found "+ids.size()+" ids...");
        DataSearcher.iterateOverSearchResults(response, hitTransformer, -1,false);


        BulkProcessor bulkProcessor = MyClient.getBulkProcessor();

        Collection<AbstractAttribute> attributes = Attributes.buildAttributes();

        final String attrString = String.join(", ", attributes.stream()
                .flatMap(attr->attr instanceof NestedAttribute ? ((NestedAttribute) attr).getAttributes().stream() : Stream.of(attr))
                .filter(attr->!(attr instanceof ConvenienceAttribute))
                .map(attr->attr instanceof SimilarityAttribute ? ((SimilarityAttribute)attr).getFieldName() : attr.getName())
                .collect(Collectors.toList())
        );

        PreparedStatement joinTableCreate = conn.prepareStatement("truncate table patents_global_exclude;");
        joinTableCreate.executeUpdate();
        joinTableCreate.close();
        conn.commit();

        PreparedStatement toExcludePs = conn.prepareStatement("insert into patents_global_exclude (exclude_id) values (?) on conflict do nothing");
        System.out.println("Starting to add exclusion ids...");
        int c = 0;
        for(String id : ids) {
            toExcludePs.setString(1, id);
            toExcludePs.executeUpdate();
            if(c % 10000==9999) {
                System.out.println("Exclusion count: "+c);
                conn.commit();
            }
        }
        toExcludePs.close();
        conn.commit();


        PreparedStatement ps = conn.prepareStatement("select "+attrString+" from patents_global_merged as p full outer join patents_global_exclude as e on (p.publication_number_full=e.exclude_id) where e.exclude_id is null");
        ps.setFetchSize(100);

        final String idField = Attributes.PUBLICATION_NUMBER_FULL;

        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        while(rs.next()) {
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Ingested: "+cnt.get());
            }
            ingest(rs,rs.getString(idField),attributes,bulkProcessor);
        }

        rs.close();
        ps.close();
        conn.close();
        MyClient.closeBulkProcessor();
        MyClient.get().close();
    }

    private static String fromSqlDate(Date date) {
        if(date==null)return null;
        return date.toLocalDate().format(DateTimeFormatter.ISO_DATE);
    }

    private static Object getValueFromResultSet(ResultSet rs, AbstractAttribute attr, String fieldName) throws SQLException {
        Object obj = null;
        try {
            obj = rs.getObject(fieldName);
        } catch(Exception e) {
            if(attr instanceof SimilarityAttribute || !(attr instanceof AbstractScriptAttribute)) {
                e.printStackTrace();
                System.exit(1);
            }
        }
        if(obj==null) return null;
        if(obj instanceof Array) {
            Object[] _array = (Object[]) ((Array) obj).getArray();
            if(_array instanceof Date[]) {
                Object[] array = new Object[_array.length];
                for(int i = 0; i < array.length; i++) {
                    array[i] = fromSqlDate((Date) _array[i]);
                }
                return array;
            }
            if(attr instanceof SimilarityAttribute) {
                // get vectors
                return BigQueryServer.vectorToFastElasticSearchObject((Number[])_array);
            }
            return _array;
        } else if(obj instanceof Date) {
            return fromSqlDate((Date) obj);
        } else if(obj instanceof Integer && attr.getType().equals("boolean")) {
            // means present
            return obj.equals(1);
        }
        return obj;
    }


    private static void ingest(ResultSet rs, String id, Collection<AbstractAttribute> attributes, BulkProcessor bulkProcessor) throws Exception {
        Map<String,Object> doc = new HashMap<>();
        for(AbstractAttribute attr : attributes) {
            final String fieldName = attr instanceof SimilarityAttribute ? ((SimilarityAttribute)attr).getFieldName() : attr.getName();
            if(attr instanceof NestedAttribute) {
                List<AbstractAttribute> children = new ArrayList<>(((NestedAttribute) attr).getAttributes());
                if(attr.isObject()) { // not nested
                    // single map
                    Map<String,Object> parentMaps = new HashMap<>();
                    for(AbstractAttribute child : children) {
                        Object val = getValueFromResultSet(rs,child,child.getName());
                        if(val!=null) {
                            parentMaps.put(child.getName(),val);
                        }
                    }
                    doc.put(fieldName,parentMaps);
                } else { // nested
                    // list of maps
                    List<Map<String,Object>> parentMaps = new ArrayList<>();
                    Object[][] childData = new Object[children.size()][];
                    int maxLength = 0;
                    for(int i = 0; i < childData.length; i++) {
                        AbstractAttribute child = children.get(i);
                        Object val = getValueFromResultSet(rs,child,child.getName());
                        if(val!=null) {
                            childData[i]=(Object[])val;
                            maxLength = Math.max(maxLength,childData[i].length);
                        }
                    }
                    if(maxLength>0) {
                        for(int i = 0; i < maxLength; i++) {
                            Map<String,Object> innerMap = new HashMap<>();
                            for(int j = 0; j < childData.length; j++) {
                                if(childData[j]!=null) {
                                    innerMap.put(children.get(j).getName(),childData[j][i]);
                                }
                            }
                            parentMaps.add(innerMap);
                        }
                        doc.put(fieldName,parentMaps);
                    }
                }
            } else {
                Object val = getValueFromResultSet(rs,attr,fieldName);
                if(val!=null) {
                    doc.put(fieldName,val);
                }
            }
        }
        IndexRequest request = new IndexRequest(IngestPatents.INDEX_NAME,IngestPatents.TYPE_NAME,id);
        request = request.source(doc);
        bulkProcessor.add(request);
    }
}
