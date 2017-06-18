package ui_models.portfolios.attributes;

import genetics.GeneticAlgorithm;
import genetics.lead_development.CompanySolution;
import genetics.lead_development.CompanySolutionCreator;
import genetics.lead_development.CompanySolutionListener;
import genetics.lead_development.ValueAttribute;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import similarity_models.AbstractSimilarityModel;
import similarity_models.BaseSimilarityModel;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.classification.ClassificationAttr;
import ui_models.attributes.value.CompDBAssetsPurchasedEvaluator;
import ui_models.attributes.value.SimilarityEvaluator;
import ui_models.attributes.value.ValueAttr;

import java.util.*;

/**
 * Created by ehallmark on 6/15/17.
 */
public class LikelyBuyerAttribute implements AbstractAttribute<String> {
    private Map<String,INDArray> lookupTable;
    private ValueAttr buyerModel;
    private static List<String> assignees;
    public LikelyBuyerAttribute(Map<String,INDArray> lookupTable, ValueAttr buyerModel) {
        if(assignees==null) assignees=new ArrayList<>(Database.getAssignees());
        this.lookupTable=lookupTable;
        this.buyerModel=buyerModel;
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        try {
            GeneticAlgorithm<CompanySolution> algorithm = new GeneticAlgorithm<>(new CompanySolutionCreator(Arrays.asList(new ValueAttribute(Constants.SIMILARITY,5d, new SimilarityEvaluator(Constants.SIMILARITY,lookupTable,lookupTable.get(portfolio.stream().findFirst().get()))),new ValueAttribute(Constants.COMPDB_ASSETS_PURCHASED_VALUE, 1d, buyerModel)), assignees, 10, Math.max(1, Runtime.getRuntime().availableProcessors())), 10, new CompanySolutionListener(), Math.max(1, Runtime.getRuntime().availableProcessors()));
            algorithm.simulate(1000, 0.5, 0.5);
            return algorithm.getBestSolution().getCompanyScores().get(0).getKey();
        } catch(Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    @Override
    public String getName() {
        return Constants.LIKELY_BUYER;
    }
}
