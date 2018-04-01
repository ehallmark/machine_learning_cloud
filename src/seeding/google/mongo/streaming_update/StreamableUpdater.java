package seeding.google.mongo.streaming_update;

import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface StreamableUpdater {
    String UPDATE_FOLDER = "streamable_updates/";
    List<String> getFields();
    Consumer<Document> getConsumer();
    void updateDocument(Document doc, Map<String,Object> set, Map<String,Object> unset);
    void finish();
}
