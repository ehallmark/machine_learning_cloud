package similarity_models.sim_rank;

import graphical_models.page_rank.CPCSimRankHelper;
import graphical_models.page_rank.SimRank;
import graphical_models.page_rank.SimRankHelper;
import lombok.Getter;
import lombok.NonNull;
import model.edges.Edge;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import similarity_models.AbstractSimilarityModel;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.AbstractPatent;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/20/17.
 */
public class CPCSimRankSimilarityModel extends SimRankSimilarityModel {
    public static final Map<String,Set<String>> classificationToPatentMap;
    private static Map<Edge<String>,Float> rankTable;
    static {
        Database.initializeDatabase();
        classificationToPatentMap=Database.getClassCodeToPatentMap();
        if(SimRankHelper.file.exists()) {
            rankTable = new SimRank.Loader().loadRankTable(CPCSimRankHelper.file);
        } else {
            System.out.println("WARNING: Rank table file does not exist");
            rankTable=null;
        }
    }

    public CPCSimRankSimilarityModel(String name, @NonNull Collection<String> portfolio) {
        super(name,portfolio);
    }

    @Override
    protected Map<Edge<String>,Float> getRankTable() {
        return rankTable;
    }

}
