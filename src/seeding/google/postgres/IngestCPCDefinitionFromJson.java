package seeding.google.postgres;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestJsonHelper;
import seeding.google.postgres.query_helper.QueryStream;
import seeding.google.postgres.query_helper.appliers.DefaultApplier;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class IngestCPCDefinitionFromJson extends IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
        final File dataDir = new File("/home/ehallmark/google-big-query/google-big-query/sep/");

        String[] fields = new String[]{
                "symbol",
                "title_full",
                "title_part",
                "level",
                "revised_date",
                "status",
                "parents",
                "children"
        };

        Connection conn = Database.getConn();

        String valueStr = "(?,?,?,?::integer,?::date,?,?,?)";
        String conflictStr = "(?,?,?::integer,?::date,?,?,?)";
        final String sql = "insert into big_query_cpc_definition (code,title_full,title_part,level,date_revised,status,parents,children) values "+valueStr+" on conflict (code) do update set (title_full,title_part,level,date_revised,status,parents,children) = "+conflictStr;

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
