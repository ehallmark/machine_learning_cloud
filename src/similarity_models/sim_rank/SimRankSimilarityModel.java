package similarity_models.sim_rank;

import graphical_models.page_rank.SimRank;
import graphical_models.page_rank.SimRankHelper;
import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Constants;
import seeding.Database;
import similarity_models.AbstractSimilarityModel;
import spark.Request;
import ui_models.filters.AbstractFilter;
import ui_models.portfolios.PortfolioList;
import ui_models.portfolios.items.Item;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/20/17.
 */
public class SimRankSimilarityModel implements AbstractSimilarityModel {
    private static Map<String,Set<String>> patentToCitedPatentsMap;
    private static Map<String,List<Pair<String,Float>>> similarityMap;
    static {
        if(SimRankHelper.similarityMapFile.exists()) {
            similarityMap = new SimRank.Loader().loadSimilarityMap(SimRankHelper.similarityMapFile);
        } else {
            System.out.println("WARNING: Rank table file does not exist");
            similarityMap=null;
        }
    }

    public static Map<String,Set<String>> getPatentToCitedPatentsMap() {
        if(patentToCitedPatentsMap==null) {
            patentToCitedPatentsMap=(Map<String,Set<String>>) Database.tryLoadObject(new File("patent_to_cited_patents_map.jobj"));
        }
        return patentToCitedPatentsMap;
    }

    @Getter
    private Map<String,Item> tokenMap;
    @Getter
    private String name;
    @Getter
    private List<Item> itemList;
    public SimRankSimilarityModel(@NonNull Collection<Item> candidateSet, String name) {
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        this.itemList=candidateSet instanceof List ? (List<Item>)candidateSet : new ArrayList<>(candidateSet);
        this.name=name;
        try {
            tokenMap = candidateSet.stream().map(item->{
                if(!similarityMap.containsKey(item.getName())) return null; // no info on item
                return item;
            }).filter(item->item!=null).collect(Collectors.toMap((item->item.getName()),item->item));

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            tokenMap = Collections.emptyMap();
        }
    }

    private SimRankSimilarityModel() {

    }

    @Override
    public double similarityTo(String label) {
        if(!similarityMap.containsKey(label)) {
            return 0d;
        }
        List<Float> scores = similarityMap.get(label).stream().filter(pair->tokenMap.containsKey(pair.getFirst())).map(pair->pair.getSecond()).collect(Collectors.toList());
        if(scores.isEmpty()) return 0d;

        return scores.stream().collect(Collectors.averagingDouble(f->f.doubleValue()));
    }

    protected List<Pair<Item, Double>> similarHelper(String patent, int n) {
        List<Pair<String,Float>> data = similarityMap.get(patent);
        if(data==null) return Collections.emptyList();
        return data.stream().filter(p->tokenMap.containsKey(p.getFirst())).sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(n)
                .map(p->new Pair<>(tokenMap.get(p.getFirst()),p.getSecond().doubleValue())).filter(p->p.getSecond()>0d).collect(Collectors.toList());
    }

    @Override
    public PortfolioList findSimilarPatentsTo(String item, INDArray avgVector, int limit, Collection<? extends AbstractFilter> filters) {
        return new PortfolioList(similarHelper(item,limit).stream()
                .map(pair->{
                    Item similarItem = pair.getFirst().clone();
                    similarItem.setSimilarity(pair.getSecond());
                    return similarItem;
                }).filter(p->filters.stream().allMatch(filter -> filter.shouldKeepItem(p)))
                .collect(Collectors.toList()));
    }

    @Override
    public int numItems() {
        return tokenMap.size();
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters) {
        PortfolioList finalPortfolio = ((SimRankSimilarityModel)other).tokenMap.keySet().stream().map(item->{
            return findSimilarPatentsTo(item,null,limit,filters);
        }).reduce((p1,p2)->{
            return merge(p1,p2,limit);
        }).orElse(new PortfolioList(Collections.emptyList()));
        return finalPortfolio;
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Collection<Item> scope) {
        SimRankSimilarityModel model = new SimRankSimilarityModel();
        model.itemList=scope instanceof List ? (List<Item>)scope : new ArrayList<>(scope);
        model.name=name;
        model.tokenMap = scope.stream().filter(item->tokenMap.containsKey(item.getName())).collect(Collectors.toMap(e->e.getName(),e->e));
        return new SimRankSimilarityModel(scope,name);
    }

    private static PortfolioList merge(PortfolioList p1, PortfolioList p2, int limit) {
        try {
            return p1.merge(p2, Constants.SIMILARITY, limit);
        } catch(Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Sorting: "+e.getMessage());
        }
    }
}
