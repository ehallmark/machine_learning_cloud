package seeding.ai_db_updater;

import elasticsearch.DataIngester;
import elasticsearch.MyClient;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.WebIterator;
import seeding.ai_db_updater.iterators.ZipFileIterator;
import seeding.data_downloader.AppDataDownloader;
import seeding.data_downloader.PatentDataDownloader;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;

import java.io.File;
import java.util.Collection;
import java.util.HashSet;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateBasePatentData {
    public static void ingestData(boolean seedApplications) {
        SimilarPatentServer.loadAttributes(true);
        Collection<ComputableAttribute> computableAttributes = new HashSet<>(SimilarPatentServer.getAllComputableAttributes());
        computableAttributes.forEach(attr->attr.initMaps());
        USPTOHandler.setComputableAttributes(computableAttributes);
        USPTOHandler.setLookupTable(SimilarPatentFinder.getLookupTable());
        String topLevelTag;
        if(seedApplications) {
            topLevelTag = "us-patent-application";
        } else {
            topLevelTag = "us-patent-grant";
        }
        WebIterator iterator = new ZipFileIterator(seedApplications ? new AppDataDownloader() : new PatentDataDownloader(), seedApplications ? "applications_temp" : "patents_temp");
        NestedHandler handler = new USPTOHandler(topLevelTag, seedApplications);
        handler.init();
        iterator.applyHandlers(handler);
    }

    public static void main(String[] args) {
        ingestData(false);
    }
}
