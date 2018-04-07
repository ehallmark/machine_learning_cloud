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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IngestCPCFromJson extends IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
        final File dataDir = new File("/usb2/data/google-big-query/google-patent-research/");

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.FAMILY_ID,
                Constants.CPC
        };

        String[] cpcFields = new String[]{
                Constants.CODE,
                Constants.INVENTIVE,
                Constants.TREE
        };

        int numFields = 5;

        Connection conn = Database.getConn();

        String valueStr = "("+String.join(",", IntStream.range(0,numFields).mapToObj(i->"?").collect(Collectors.toList()))+")";
        String conflictStr = "("+String.join(",", IntStream.range(0,numFields-1).mapToObj(i->"?").collect(Collectors.toList()))+")";
        final String sql = "insert into big_query_patent_to_priority_claims (publication_number_full,family_id,pc_publication_number_full,code,inventive,tree) values "+valueStr+" on conflict (publication_number_full) do update set (family_id,pc_publication_number_full,code,inventive,tree) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, new String[]{fields[1],fields[2],cpcFields[0],cpcFields[1],cpcFields[2]});
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
                IngestJsonHelper.streamJsonFile(stream,attributeFunctions).filter(map->filterDocumentFunction.apply(map)).forEach(map->{
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
