package similarity_models.sim_rank;

import graphical_models.page_rank.SimRank;
import graphical_models.page_rank.SimRankHelper;
import model.edges.Edge;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Constants;
import seeding.Database;
import similarity_models.AbstractSimilarityModel;
import similarity_models.paragraph_vectors.SimilarPatentFinder;
import ui_models.portfolios.PortfolioList;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Created by ehallmark on 4/20/17.
 */
public class SimRankSimilarityModel implements AbstractSimilarityModel {
    public static final Map<String,Set<String>> patentToCitedPatentsMap;
    private static final Map<Edge<String>,Float> rankTable;
    static {
        patentToCitedPatentsMap=(Map<String,Set<String>>) Database.tryLoadObject(new File("patent_to_cited_patents_map.jobj"));
        if(SimRankHelper.file.exists()) {
            rankTable = new SimRank.Loader().loadRankTable(SimRankHelper.file);
        } else {
            System.out.println("WARNING: Rank table file does not exist");
            rankTable=null;
        }
    }


    private List<Pair<String, Double>> similarHelper(Collection<String> items, PortfolioList.Type type, int n) {
        List<util.Pair<String,Float>> data = SimRank.findSimilarDocumentsFromRankTable(rankTable,items,n);
        List<Pair<String,Double>> toReturn = new ArrayList<>(n);
        data.forEach(p->toReturn.add(new Pair<>(p._1,p._2.doubleValue())));
        return toReturn;
    }

    @Override
    public List<PortfolioList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, Collection<String> badAssets, PortfolioList.Type portfolioType) {
        return null;
    }

    @Override
    public PortfolioList similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, Collection<String> badLabels, PortfolioList.Type portfolioType) {
        return null;
    }

    @Override
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, Collection<String> labelsToExclude, double threshold, int limit, PortfolioList.Type portfolioType) {
        return null;
    }
}
