package analysis.tech_tagger;

import com.googlecode.concurrenttrees.radix.RadixTree;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.MinHeap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Evan on 2/18/2017.
 */
public class GatherTagger {
    private static RadixTree<INDArray> classCodeProbabilities;
    private static List<String> technologies;
    private static final double DECAY_RATE = 1.4d;
    static {
        classCodeProbabilities=(RadixTree<INDArray>) Database.tryLoadObject(BuildCPCToGatherStatistics.file);
        technologies=(List<String>)Database.tryLoadObject(BuildCPCToGatherStatistics.techListFile);
    }

    public String predictTechnology(String patent) {
        List<String> predictions = predictTechnologies(patent,1);
        if(predictions.isEmpty()) return "";
        return predictions.get(0);
    }

    public List<String> predictTechnologies(String patent, int n) {
        final int MIN_CLASS_SIZE = BuildCPCToGatherStatistics.MIN_CLASS_CODE_LENGTH;
        final int numTechnologies = technologies.size();
        List<String> predictions = new ArrayList<>(n);
        Collection<String> classCodes = Database.classificationsFor(patent);
        if(!classCodes.isEmpty()) {
            classCodes.forEach(cpc->{
                cpc=cpc.trim();
                // get parent classes and weight probabilities
                double weight = 1.0;
                double totalWeight = 0d;
                INDArray probVec = Nd4j.zeros(numTechnologies);
                while(cpc.length()>=MIN_CLASS_SIZE) {
                    INDArray vec = classCodeProbabilities.getValueForExactKey(cpc);
                    if(vec!=null) {
                        vec.muli(weight);
                        totalWeight+=weight;
                        probVec.addi(vec);
                    }
                    weight/=DECAY_RATE;
                    cpc=cpc.substring(0,cpc.length()-1).trim();
                }
                if(totalWeight>0) {
                    probVec.divi(totalWeight);
                    // find index of max values
                    MinHeap<Technology> heap = new MinHeap<>(n);
                    for(int i = 0; i < technologies.size(); i++) {
                        String tech = technologies.get(i);
                        heap.add(new Technology(tech,probVec.getDouble(i)));
                    }
                    while(!heap.isEmpty()) {
                        predictions.add(0,heap.remove().name);
                    }
                }
            });
        }
        return predictions;
    }

    class Technology implements Comparable<Technology> {
        double score;
        String name;

        Technology(String name, double score) {
            this.score=score;
            this.name=name;
        }

        @Override
        public int compareTo(Technology o) {
            return Double.compare(score,o.score);
        }
    }
}
