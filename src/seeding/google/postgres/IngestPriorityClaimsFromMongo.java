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
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class IngestPriorityClaimsFromMongo {

    public static void main(String[] args) throws SQLException {
        final String index = IngestPatents.INDEX_NAME;
        final String type = IngestPatents.TYPE_NAME;

        String[] fields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.FAMILY_ID,
                Constants.PRIORITY_CLAIM
        };

        String[] priorityClaimFields = new String[]{
                Constants.FULL_PUBLICATION_NUMBER,
                Constants.FULL_APPLICATION_NUMBER,
                Constants.FILING_DATE
        };

        int numFields = 5;

        Connection conn = Database.getConn();

        String valueStr = "("+String.join(",", IntStream.range(0,numFields).mapToObj(i->"?").collect(Collectors.toList()))+")";
        String conflictStr = "("+String.join(",", IntStream.range(0,numFields-1).mapToObj(i->"?").collect(Collectors.toList()))+")";
        PreparedStatement ps = conn.prepareStatement("insert into big_query_patent_to_priority_claims (publication_number_full,family_id,pc_publication_number_full,pc_application_number_full,pc_filing_date) values "+valueStr+" on conflict (publication_number_full) do update set (family_id,pc_publication_number_full,pc_application_number_full,pc_filing_date) = "+conflictStr);

        DefaultApplier applier = new DefaultApplier(true, conn, new String[]{fields[1],fields[2],priorityClaimFields[0],priorityClaimFields[1],priorityClaimFields[2]});
        QueryStream<List<Object>> queryStream = new QueryStream<>(ps,applier);


        Consumer<Document> consumer = doc -> {
            try {
                String publicationNumber = doc.getString(fields[0]);
                String familyId = doc.getString(fields[1]);
                List<Map<String,Object>> nestedData = (List<Map<String,Object>>) doc.get(fields[2]);
                if(nestedData!=null) {
                    for(Map<String,Object> map : nestedData) {
                        List<Object> data = new ArrayList<>(numFields);
                        data.add(publicationNumber);
                        data.add(familyId);
                        for(int i = 0; i < priorityClaimFields.length; i++) {
                            data.add(map.get(priorityClaimFields[0]));
                        }
                        queryStream.ingest(data);
                    }
                }
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
