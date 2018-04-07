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
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class IngestCitationsFromJson extends IngestPatentsFromJson {

    public static void main(String[] args) throws SQLException {
        final File dataDir = new File("/home/ehallmark/google-big-query/google-big-query/google-patents-research/");

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.FAMILY_ID,
                Constants.CITATION
        };

        String[] citationFields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.FULL_APPLICATION_NUMBER,
                Constants.NPL_TEXT,
                Constants.TYPE,
                Constants.CATEGORY,
                Constants.FILING_DATE
        };

        Connection conn = Database.getConn();

        String valueStr = "(?,?,?,?,?,?,?,?::date)";
        String conflictStr = "(?,?,?,?,?,?,?::date)";
        final String sql = "insert into big_query_patent_to_citations (publication_number_full,family_id,cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date) values "+valueStr+" on conflict (publication_number_full) do update set (family_id,cited_publication_number_full,cited_application_number_full,cited_npl_text,cited_type,cited_category,cited_filing_date) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, new String[]{fields[1],fields[2],citationFields[0],citationFields[1],citationFields[2],citationFields[3],citationFields[4],citationFields[5]});
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);

        Consumer<Document> consumer = doc -> {
            try {
                List<Map<String,Object>> maps = (List<Map<String,Object>>)doc.get(fields[2]);
                if(maps!=null) {
                    for(Map<String,Object> map: maps) {
                        List<Object> data = new ArrayList<>(fields.length);
                        data.add(doc.get(fields[0]));
                        data.add(doc.get(fields[1]));
                        data.add(map.get(citationFields[0]));
                        data.add(map.get(citationFields[1]));
                        data.add(map.get(citationFields[2]));
                        data.add(map.get(citationFields[3]));
                        data.add(map.get(citationFields[4]));
                        data.add(map.get(citationFields[5]));
                        queryStream.ingest(data);
                    }
                }
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
