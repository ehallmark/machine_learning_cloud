package seeding.google.postgres;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.IngestPatents;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.function.Consumer;

public class IngestSEPFromMongo {

    public static void main(String[] args) throws SQLException {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = new String[]{
                "record_id",
                Constants.FAMILY_ID,
                "disclosure_event",
                "sso",
                "patent_owner_harmonized",
                "patent_owner_unharmonized",
                "date",
                "standard",
                "licensing_commitment",
                "blanket_type",
                "blanket_scope",
                "third_party",
                "reciprocity",
                "publication_number"
        };

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("insert into big_query_sep ("+String.join(",",fields)+") values (?,?,?,?,?,?,?::date,?,?,?::integer,?,?::boolean,?::boolean,?) on conflict (record_id) do update set ("+ String.join(",",Arrays.copyOfRange(fields,1,fields.length))+") = (?,?,?,?,?,?::date,?,?,?::integer,?,?::boolean,?::boolean,?)");
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
