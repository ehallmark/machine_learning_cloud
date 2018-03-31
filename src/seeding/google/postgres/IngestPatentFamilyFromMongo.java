package seeding.google.postgres;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestPatents;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;

public class IngestPatentFamilyFromMongo {

    public static void main(String[] args) throws SQLException {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.PUBLICATION_NUMBER_WITH_COUNTRY,
                Constants.APPLICATION_NUMBER_WITH_COUNTRY,
                Constants.COUNTRY_CODE,
                Constants.KIND_CODE,
                Constants.APPLICATION_KIND,
                Constants.FAMILY_ID
        };

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into big_query_patent_family ("+String.join(",",fields)+") values (?,?,?,?,?,?,?) on conflict ("+fields[0]+") do update set ("+ String.join(",", Arrays.copyOfRange(fields,1,fields.length))+") = (?,?,?,?,?,?)");
        Consumer<Document> consumer = doc -> {
            try {
                for(int i = 0; i < fields.length; i++) {
                    String val = doc.getString(fields[i]);
                    ps.setString(1+i, val);
                    if(i>0) {
                        ps.setString(i+fields.length, val);
                    }
                }
                ps.executeUpdate();
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer, new Document(), index, type, fields);

        ps.close();
        conn.close();
    }

}
