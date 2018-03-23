package seeding.google;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;

import java.io.File;

import static seeding.google.IngestJsonHelper.ingestJsonDump;

public class IngestCPCDefinitions {
    public static final String INDEX_NAME = "patents";
    public static final String TYPE_NAME = "cpc_def";

    public static void main(String[] args) {
        final String idField = "symbol";
        final File dataDir = new File("/media/ehallmark/My Passport/data/google-big-query/cpc/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);

        ingestJsonDump(idField,dataDir,collection,true);
    }
}
