package seeding.google.postgres;

import elasticsearch.IngestMongoIntoElasticSearch;
import org.bson.Document;
import seeding.Database;
import seeding.google.attributes.Constants;
import seeding.google.mongo.ingest.IngestPatents;
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

public class IngestPatentsFromMongo {

    public static void main(String[] args) throws SQLException {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.PUBLICATION_NUMBER,
                Constants.FULL_APPLICATION_NUMBER,
                Constants.APPLICATION_NUMBER,
                Constants.APPLICATION_NUMBER_FORMATTED,
                Constants.FILING_DATE,
                Constants.PUBLICATION_DATE,
                Constants.PRIORITY_DATE,
                Constants.COUNTRY_CODE,
                Constants.KIND_CODE,
                Constants.APPLICATION_KIND,
                Constants.FAMILY_ID,
                Constants.ENTITY_STATUS
        };

        Connection conn = Database.getConn();

        String valueStr = "(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        String conflictStr = "(?,?,?,?,?,?,?,?,?,?,?,?)";
        final String sql = "insert into big_query_patents (publication_number_full,publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,original_entity_type) values "+valueStr+" on conflict (publication_number_full) do update set (publication_number,application_number_full,application_number,application_number_formatted,filing_date,publication_date,priority_date,country_code,kind_code,application_kind,family_id,original_entity_type) = "+conflictStr;

        DefaultApplier applier = new DefaultApplier(true, conn, fields);
        QueryStream<List<Object>> queryStream = new QueryStream<>(sql,conn,applier);


        Consumer<Document> consumer = doc -> {
            try {
                List<Object> data = new ArrayList<>(fields.length);
                for(int i = 0; i < fields.length; i++) {
                    Object val = doc.get(fields[i]);
                    if(i==7) { // priority date
                        if(val==null||val.equals("0")) {
                            // default to filing date
                            val = data.get(5);
                        }
                    }
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
