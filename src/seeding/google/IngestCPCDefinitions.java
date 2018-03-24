package seeding.google;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;

import java.io.File;
import java.util.Collections;

import static seeding.google.IngestJsonHelper.ingestJsonDump;

public class IngestCPCDefinitions {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "cpc";

    public static void main(String[] args) {
        final String idField = "symbol";
        final File dataDir = new File("/usb2/data/google-big-query/cpc/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);

        ingestJsonDump(idField,dataDir,collection,true,null, Collections.emptyList());
    }
}
