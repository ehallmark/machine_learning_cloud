package test;

import elasticsearch.CreatePatentDBIndex;
import elasticsearch.IngestAttributeData;
import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateElasticSearch {

    public static void main(String[] args) {
        //PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        //patentIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.patents));
        //PatentGrantIterator applicationIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        //applicationIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.applications));

        IngestAttributeData.main(args);
    }
}
