package seeding.google.postgres;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestSEP;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IngestSEPFromMongo {

    public static void main(String[] args) throws SQLException {
        final String index = IngestSEP.INDEX_NAME;
        final String type = IngestSEP.TYPE_NAME;

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
                "pub_cleaned"
        };

        Connection conn = Database.getConn();

        String valueStr = "("+String.join(",", IntStream.range(0,fields.length).mapToObj(i->"?").collect(Collectors.toList()))+")";
        String conflictStr = "("+String.join(",", IntStream.range(0,fields.length-1).mapToObj(i->"?").collect(Collectors.toList()))+")";
        final String sql = "insert into big_query_sep (record_id,family_id,disclosure_event,sso,patent_owner_harmonized,patent_owner_unharmonized,date,standard,licensing_commitment,blanket_type,blanket_scope,third_party,reciprocity,publication_number_with_country) values "+valueStr+" on conflict (record_id) do update set (family_id,disclosure_event,sso,patent_owner_harmonized,patent_owner_unharmonized,date,standard,licensing_commitment,blanket_type,blanket_scope,third_party,reciprocity,publication_number_with_country) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);


        Consumer<Document> consumer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
                    Object val = doc.get(fields[i]);
                    data.add(val);
                }
                queryStream.ingest(data);
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        };

        IngestMongoIntoElasticSearch.iterateOverCollection(consumer, new Document(), index, type, fields);

        queryStream.close();
        conn.close();
    }

}
