package similarity_models.sim_rank;

import graphical_models.page_rank.SimRank;
import graphical_models.page_rank.SimRankHelper;
import lombok.Getter;
import lombok.NonNull;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;
import similarity_models.AbstractSimilarityModel;
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
    public SimRankSimilarityModel(@NonNull Collection<String> candidateSet, String name) {
        if(candidateSet==null) throw new NullPointerException("candidateSet");
        candidateSet=candidateSet.stream().distinct().collect(Collectors.toList());
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        try {
            tokenMap = candidateSet.stream().map(itemStr->{
                if(!similarityMap.containsKey(itemStr)) return null; // no info on item
                return itemStr;
            }).filter(item->item!=null).map(item->new Item(item)).collect(Collectors.toMap((item->item.getName()),item->item));

        } catch (Exception e) {
            e.printStackTrace();
            // errors
            tokenMap = Collections.emptyMap();
        }
    }

    protected List<Pair<Item, Double>> similarHelper(String patent, int n) {
        List<Pair<String,Float>> data = similarityMap.get(patent);
        if(data==null) return Collections.emptyList();
        return data.stream().sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).limit(n)
                .map(p->new Pair<>(tokenMap.get(p.getFirst()),p.getSecond().doubleValue())).collect(Collectors.toList());
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
    public Collection<String> getTokens() {
        return tokenMap.keySet();
    }

    @Override
    public PortfolioList similarFromCandidateSet(AbstractSimilarityModel other, int limit, Collection<? extends AbstractFilter> filters) {
        PortfolioList finalPortfolio = other.getTokens().stream().map(item->{
            return findSimilarPatentsTo(item,null,limit,filters);
        }).reduce((p1,p2)->{
            return merge(p1,p2,limit);
        }).orElse(new PortfolioList(Collections.emptyList()));
        return finalPortfolio;
    }

    @Override
    public AbstractSimilarityModel duplicateWithScope(Collection<String> scope) {
        return new SimRankSimilarityModel(scope,name);
    }

    private static PortfolioList merge(PortfolioList p1, PortfolioList p2, int limit) {
        Map<String,Item> scoreMap = new HashMap<>();
        p1.getItemList().forEach(item->{
            scoreMap.put(item.getName(),item);
        });
        p2.getItemList().forEach(item->{
            if(scoreMap.containsKey(item.getName())) {
                Item dup = scoreMap.get(item.getName());
                dup.setSimilarity(dup.getSimilarity()+item.getSimilarity());
            } else {
                scoreMap.put(item.getName(),item);
            }
        });
        return new PortfolioList(scoreMap.values().stream()
                .sorted(Item.similarityComparator()).limit(limit).collect(Collectors.toList()));
    }
}
