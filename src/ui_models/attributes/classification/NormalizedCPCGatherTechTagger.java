package ui_models.attributes.classification;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import seeding.Database;
import ui_models.attributes.value.ValueMapNormalizer;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/18/2017.
 */
public class NormalizedCPCGatherTechTagger extends CPCGatherTechTagger {
    protected static final Map<String,INDArray> normalizedMap;
    static final File normalizedMapFile = new File("normalized_gather_tech_map.jobj");
    static {
        if(normalizedMapFile.exists()) {
            normalizedMap = (Map<String,INDArray>)Database.tryLoadObject(normalizedMapFile);
        } else {
            normalizedMap = new HashMap<>();
            int numTech = DEFAULT_ORDERED_TECHNOLOGIES.size();
            List<String> assignees = new ArrayList<>(Database.getAssignees());
            INDArray vec = Nd4j.create(assignees.size(), numTech);
            AtomicInteger row = new AtomicInteger(0);
            System.out.println("Normalizing gather tech map...");
            for (String assignee : assignees) {
                if (DEFAULT_TECHNOLOGY_MAP.containsKey(assignee)) {
                    INDArray techVec = DEFAULT_TECHNOLOGY_MAP.get(assignee);
                    // check if there is any relevance at all
                    vec.putRow(row.getAndIncrement(), techVec);
                }
            }
            vec = vec.get(NDArrayIndex.interval(0, row.get(), false), NDArrayIndex.all());
            System.out.println("Calculating statistics...");
            INDArray stds = vec.std(0);
            INDArray means = vec.mean(0);
            double end = ValueMapNormalizer.DEFAULT_END;
            double start = ValueMapNormalizer.DEFAULT_START;
            RealDistribution[] distributions = new RealDistribution[numTech];
            for (int i = 0; i < numTech; i++) {
                distributions[i] = new NormalDistribution(stds.getDouble(i), means.getDouble(i));
            }
            final double relevanceThreshold = 0d;
            for (String assignee : assignees) {
                INDArray newVec = Nd4j.create(numTech);
                if (DEFAULT_TECHNOLOGY_MAP.containsKey(assignee)) {
                    INDArray techVec = DEFAULT_TECHNOLOGY_MAP.get(assignee);
                    // check if there is any relevance at all
                    if (techVec.sub(means).div(stds).maxNumber().doubleValue() > relevanceThreshold) {
                        System.out.print("RELEVANT");
                        for (int i = 0; i < numTech; i++) {
                            RealDistribution distribution = distributions[i];
                            double value = distribution.cumulativeProbability(techVec.getDouble(i)) * (end - start) + start;
                            newVec.putScalar(i, value);
                        }
                        normalizedMap.put(assignee, newVec);
                    }
                }
                System.out.println(" Assignee: " + assignee);
            }
            System.out.println("Finished Calculating Statistics...");
            Database.trySaveObject(normalizedMap,normalizedMapFile);
        }
    }
    private NormalizedCPCGatherTechTagger() {
        super(normalizedMap,DEFAULT_ORDERED_TECHNOLOGIES);
    }

}
