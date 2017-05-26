package similarity_models.sim_rank;

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

    private Collection<String> portfolio;
    @Getter
    private String name;
    public SimRankSimilarityModel(String name, @NonNull Collection<String> portfolio) {
        this.portfolio=portfolio;
        this.name=name;
    }

    private List<Pair<String, Double>> similarHelper(String patent, int n) {
        List<util.Pair<String,Float>> data = SimRank.findSimilarDocumentsFromRankTable(rankTable,Arrays.asList(patent),n);
        List<Pair<String,Double>> toReturn = new ArrayList<>(n);
        data.forEach(p->{
            toReturn.add(new Pair<>(p._1,p._2.doubleValue()));
        });
        return toReturn;
    }

    @Override
    public PortfolioList findSimilarPatentsTo(String patentNumber, INDArray avgVector, int limit, PortfolioList.Type portfolioType, Collection<? extends AbstractFilter> filters) {
        if(portfolioType.equals(PortfolioList.Type.patents)) {
            return new PortfolioList(similarHelper(patentNumber,limit).stream()
                    .map(pair->new AbstractPatent(pair.getFirst(),pair.getSecond(),patentNumber))
                    .filter(p->filters.stream().allMatch(filter -> filter.shouldKeepItem(p)))
                    .collect(Collectors.toList()), portfolioType);
        } else return new PortfolioList(Collections.emptyList(),portfolioType);
    }

    @Override
    public int numItems() {
        return portfolio.size();
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, PortfolioList.Type portfolioType, int limit, Collection<? extends AbstractFilter> filters) {
        SimRankSimilarityModel otherModel = (SimRankSimilarityModel)other;
        if(!portfolioType.equals(PortfolioList.Type.patents)) {
            return new PortfolioList(Collections.emptyList(), portfolioType);
        }
        return otherModel.portfolio.stream().map(item->{
            return findSimilarPatentsTo(item,null,limit,portfolioType,filters);
        }).reduce((p1,p2)->{
            return p1.merge(p2,limit);
        }).orElse(new PortfolioList(Collections.emptyList(),portfolioType));
    }


}
