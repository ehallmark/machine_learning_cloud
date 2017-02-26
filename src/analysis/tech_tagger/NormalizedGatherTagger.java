package analysis.tech_tagger;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.MinHeap;
import tools.PortfolioList;
import value_estimation.ValueMapNormalizer;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/18/2017.
 */
public class NormalizedGatherTagger extends GatherTagger {
    protected static final Map<String,INDArray> normalizedMap;
    static {
        normalizedMap=new HashMap<>();
        int numTech = DEFAULT_ORDERED_TECHNOLOGIES.size();
        List<String> assignees = new ArrayList<>(Database.getAssignees());
        INDArray vec = Nd4j.create(assignees.size(),numTech);
        AtomicInteger row = new AtomicInteger(0);
        System.out.println("Normalizing gather tech map...");
        assignees.forEach(assignee->{
            if(DEFAULT_TECHNOLOGY_MAP.containsKey(assignee)) {
                vec.putRow(row.getAndIncrement(),DEFAULT_TECHNOLOGY_MAP.get(assignee));
            } 
        });
        vec.reshape(row.get(),numTech);
        System.out.println("Calculating statistics...");
        INDArray vars = vec.var(0);
        INDArray means = vec.mean(0);
        double end = ValueMapNormalizer.DEFAULT_END;
        double start = ValueMapNormalizer.DEFAULT_START;
        AtomicInteger cnt = new AtomicInteger(0);
        assignees.forEach(assignee->{
            INDArray newVec = Nd4j.create(numTech);
            if(DEFAULT_TECHNOLOGY_MAP.containsKey(assignee)) {
                INDArray techVec = vec.getRow(cnt.getAndIncrement());
                for(int i = 0; i < numTech; i++) {
                    RealDistribution distribution = new NormalDistribution(vars.getDouble(i),means.getDouble(i));
                    double value = distribution.cumulativeProbability(techVec.getDouble(i))*(end-start)+start;
                    newVec.putScalar(i,value);
                }
            } else {
                for(int i = 0; i < numTech; i++) {
                    newVec.putScalar(i,start);
                }
            }
            normalizedMap.put(assignee,newVec);
        });
    }
    public NormalizedGatherTagger() {
        super(normalizedMap,DEFAULT_ORDERED_TECHNOLOGIES);
    }

}
