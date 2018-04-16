package seeding.google.postgres;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.mongo.ingest.IngestJsonHelper;
import seeding.google.mongo.ingest.IngestSEP;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IngestSEPFromJson extends IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
        final File dataDir = new File("/home/ehallmark/google-big-query/google-big-query/sep/");

        String[] fields = new String[]{
                "record_id",
                SeedingConstants.FAMILY_ID,
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

        String valueStr = "(?,?,?,?,?,?,?::date,?,?,?::integer,?,?::boolean,?::boolean,?)";
        String conflictStr = "(?,?,?,?,?,?::date,?,?,?::integer,?,?::boolean,?::boolean,?)";
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

        Stream.of(dataDir.listFiles()).forEach(file-> {
            try(InputStream stream = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(file)))) {
                IngestJsonHelper.streamJsonFile(stream,attributeFunctions).forEach(map->{
                    consumer.accept(new Document(map));
                });

            } catch(Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        });

        queryStream.close();
        conn.close();
    }

}
