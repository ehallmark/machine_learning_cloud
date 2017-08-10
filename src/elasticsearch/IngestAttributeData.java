package elasticsearch;

import models.similarity_models.paragraph_vectors.SimilarPatentFinder;
import org.nd4j.linalg.api.ndarray.INDArray;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 7/23/2017.
 */
public class IngestAttributeData {
    private static final int batchSize = 10000;
    public static void main(String[] args) {
        SimilarPatentServer.initialize(true,false);
        boolean loadVectors = true;
        Map<String,INDArray> lookupTable = loadVectors ? SimilarPatentFinder.getLookupTable() : null;
        SimilarPatentServer.loadAndIngestAllItemsWithAttributes(lookupTable,batchSize,SimilarPatentServer.getAllComputableAttributeNames(),loadVectors);
    }

}
