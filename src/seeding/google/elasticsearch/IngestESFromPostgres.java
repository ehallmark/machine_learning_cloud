package seeding.google.elasticsearch;

import elasticsearch.MyClient;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import seeding.Database;
import seeding.google.mongo.ingest.IngestPatents;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class IngestESFromPostgres {

    public static void main(String[] args) throws Exception {
        Connection conn = Database.getConn();
        BulkProcessor bulkProcessor = MyClient.getBulkProcessor();

        Collection<AbstractAttribute> attributes = Attributes.buildAttributes();

        PreparedStatement ps = conn.prepareStatement("select * from patents_global_merged");
        ps.setFetchSize(10);

        final String idField = Attributes.PUBLICATION_NUMBER_FULL;

        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            ingest(rs,rs.getString(idField),attributes,bulkProcessor);
        }

        rs.close();
        ps.close();
        conn.close();
        MyClient.closeBulkProcessor();
        MyClient.get().close();
    }

    private static String fromSqlDate(Date date) {
        return LocalDate.from(date.toInstant()).format(DateTimeFormatter.ISO_DATE);
    }

    private static Object getValueFromResultSet(ResultSet rs, AbstractAttribute attr) throws SQLException {
        Object obj = rs.getObject(attr.getName());
        if(obj==null) return null;
        if(obj instanceof Array) {
            Object[] array = (Object[]) ((Array) obj).getArray();
            if(array instanceof Date[]) {
                for(int i = 0; i < array.length; i++) {
                    array[i]=fromSqlDate((Date)array[i]);
                }
            }
            return ((Array) obj).getArray();
        } else if(obj instanceof Date) {
            return fromSqlDate((Date) obj);
        }
        return obj;
    }


    private static void ingest(ResultSet rs, String id, Collection<AbstractAttribute> attributes, BulkProcessor bulkProcessor) throws Exception {
        Map<String,Object> doc = new HashMap<>();
        for(AbstractAttribute attr : attributes) {
            if(attr instanceof NestedAttribute) {
                List<AbstractAttribute> children = new ArrayList<>(((NestedAttribute) attr).getAttributes());
                if(attr.isObject()) { // not nested
                    // single map
                    Map<String,Object> parentMaps = new HashMap<>();
                    for(AbstractAttribute child : children) {
                        Object val = getValueFromResultSet(rs,child);
                        if(val!=null) {
                            parentMaps.put(child.getName(),val);
                        }
                    }
                    doc.put(attr.getName(),parentMaps);
                } else { // nested
                    // list of maps
                    List<Map<String,Object>> parentMaps = new ArrayList<>();
                    Object[][] childData = new Object[children.size()][];
                    int maxLength = 0;
                    for(int i = 0; i < childData.length; i++) {
                        AbstractAttribute child = children.get(i);
                        Object val = getValueFromResultSet(rs,child);
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
                        doc.put(attr.getName(),parentMaps);
                    }
                }
            } else {
                Object val = getValueFromResultSet(rs,attr);
                if(val!=null) {
                    doc.put(attr.getName(),val);
                }
            }
        }
        IndexRequest request = new IndexRequest(IngestPatents.INDEX_NAME,IngestPatents.TYPE_NAME,id);
        request = request.source(doc);
        bulkProcessor.add(request);
    }
}
