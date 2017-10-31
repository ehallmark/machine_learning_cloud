package seeding;

import elasticsearch.DataIngester;
import elasticsearch.MongoDBClient;
import seeding.data_downloader.*;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 10/31/17.
 */
public class CleanseAttributesAndMongoBeforeReseed {
    public static void main(String[] args) {
        // cleanse mongo
        DataIngester.clearMongoDB();

        // clear data downloaders cache
        Collection<FileStreamDataDownloader> downloaders = Arrays.asList(
                new AssignmentDataDownloader(),
                new PatentDataDownloader(),
                new AppDataDownloader()
        );
        downloaders.forEach(FileStreamDataDownloader::clearCache);

        // cleanse attributes
        SimilarPatentServer.initialize(true,false);
        Collection<ComputableAttribute<?>> attributes = SimilarPatentServer.getAllComputableAttributes();
        attributes.forEach(attr->{
            if(attr.shouldCleanseBeforeReseed()) {
                attr.cleanse();
            }
        });
    }
}
