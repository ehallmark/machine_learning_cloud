package ui_models.attributes.classification.helper;

import com.google.common.util.concurrent.AtomicDouble;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/18/2017.
 */
public class BuildCPCToGatherStatistics {
    public static final File techListFile = new File("ordered_gather_cpc_to_tech_list.jobj");
    public static final File techMapFile = new File("gather_cpc_to_tech_map.jobj");

    // compute probability of T given C
    public static void handleCPCS(Collection<String> cpcGroup, List<String> orderedTechnologies, Map<String,Collection<String>> gatherTechMap, Map<String,Collection<String>> allCPCsToPatentsMap, Map<String,INDArray> classCodeToCondProbMap) {
        cpcGroup.forEach(cpc->{
            Collection<String> patentsInClass = allCPCsToPatentsMap.get(cpc);
            int classSize = patentsInClass.size();
            System.out.println("Num patents for "+cpc+": "+patentsInClass.size());
            // go through technologies
            INDArray probabilityVector = Nd4j.create(orderedTechnologies.size());
            for(int i = 0; i < orderedTechnologies.size(); i++) {
                String tech = orderedTechnologies.get(i);
                Collection<String> patentsInTech = gatherTechMap.get(tech);
                int techSize = patentsInTech.size();
                AtomicInteger cnt = new AtomicInteger(0);
                patentsInTech.forEach(p->{
                    if(patentsInClass.contains(p)) {
                        cnt.getAndIncrement();
                    }
                });
                double probability = new Double(cnt.get())*Math.log(1.0+Database.subClassificationsForClass(cpc).size())/(classSize*techSize);
                probabilityVector.putScalar(i,probability);
            }
            classCodeToCondProbMap.put(cpc,probabilityVector);
        });
    }

    public static INDArray predictTechnologiesVectorForPatent(List<String> technologies, Map<String,INDArray> classProbabilities,  String patent, double decayRate, int minCPCSize) {
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
                while(cpc.length()>=minCPCSize) {
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
