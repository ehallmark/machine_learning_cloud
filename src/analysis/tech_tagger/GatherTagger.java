package analysis.tech_tagger;

import com.google.common.util.concurrent.AtomicDouble;
import com.googlecode.concurrenttrees.radix.RadixTree;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.MinHeap;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/18/2017.
 */
public class GatherTagger {
    private static Map<String,INDArray> technologyMap;
    private static List<String> orderedTechnologies;
    static {
        technologyMap=(Map<String,INDArray>)Database.tryLoadObject(BuildCPCToGatherStatistics.techMapFile);
        orderedTechnologies=(List<String>)Database.tryLoadObject(BuildCPCToGatherStatistics.techListFile);

    }


    public static List<Pair<String,Double>> getTechnologiesFor(String item, int n) {
        if(item==null)return Collections.emptyList();
        return topTechnologies(technologyMap.get(item),n);
    }

    private static List<String> getTechnologiesAsStringsFor(String item, int n) {
        return getTechnologiesFor(item,n).stream().map(pair->pair.getFirst()).collect(Collectors.toList());
    }

    private static List<Pair<String,Double>> topTechnologies(INDArray vec, int n) {
        if(vec==null)return Collections.emptyList();
        MinHeap<Technology> heap = new MinHeap<>(n);
        for(int i = 0; i < orderedTechnologies.size(); i++) {
            String tech = orderedTechnologies.get(i);
            heap.add(new Technology(tech,vec.getDouble(i)));
        }
        List<Pair<String,Double>> predictions = new ArrayList<>(n);
        while(!heap.isEmpty()) {
            Technology tech = heap.remove();
            predictions.add(0,new Pair<>(tech.name,tech.score));
        }
        return predictions;
    }

    static INDArray predictTechnologiesVectorForPatent(List<String> technologies, Map<String,INDArray> classProbabilities,  String patent, double decayRate, int n) {
        final int MIN_CLASS_SIZE = BuildCPCToGatherStatistics.MIN_CLASS_CODE_LENGTH;
        final int numTechnologies = technologies.size();
        Collection<String> classCodes = Database.classificationsFor(patent);
        AtomicDouble overallWeight = new AtomicDouble(0d);
        INDArray probVec = Nd4j.zeros(numTechnologies);
        if(!classCodes.isEmpty()) {
            classCodes.forEach(cpc->{
                cpc=cpc.trim();
                // get parent classes and weight probabilities
                double weight = 1.0;
                double totalWeight = 0d;
                while(cpc.length()>=MIN_CLASS_SIZE) {
                    INDArray vec = classProbabilities.get(cpc);
                    if(vec!=null) {
                        vec.muli(weight);
                        totalWeight+=weight;
                        probVec.addi(vec);
                    }
                    weight/=decayRate;
                    cpc=cpc.substring(0,cpc.length()-1).trim();
                }
                if(totalWeight>0) {
                    overallWeight.getAndAdd(totalWeight);
                }
            });
        }
        if(overallWeight.get()<=0) return null;

        probVec.divi(overallWeight.get());
        return probVec;
    }

}
