package seeding.google.mongo.ingest;

import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoCollection;
import elasticsearch.MongoDBClient;

import java.io.File;
import java.util.Collections;

import static seeding.google.mongo.ingest.IngestJsonHelper.ingestJsonDump;

public class IngestSEP {
    public static final String INDEX_NAME = "big_query";
    public static final String TYPE_NAME = "sep";

    public static void main(String[] args) {
        final String idField = "record_id";
        final File dataDir = new File("/usb2/data/google-big-query/sep/");
        final MongoClient client = MongoDBClient.get();
        final MongoCollection collection = client.getDatabase(INDEX_NAME).getCollection(TYPE_NAME);

        ingestJsonDump(idField,dataDir,collection,true,null, Collections.emptyList());
    }
}
