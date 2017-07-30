package test;

import elasticsearch.CreatePatentDBIndex;
import elasticsearch.IngestAttributeData;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateElasticSearch {

    public static void main(String[] args) {
        SimilarPatentServer.initialize(true);
        Map<String,INDArray> lookupTable = SimilarPatentFinder.getLookupTable();
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(lookupTable,10000, Arrays.asList(Constants.ASSIGNEE_ENTITY_TYPE,Constants.AI_VALUE),false);
    }
}
