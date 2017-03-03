package analysis.tech_tagger;

import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import com.google.common.util.concurrent.AtomicDouble;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.ClassCodeHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/18/2017.
 */
public class BuildCPCToGatherStatistics {
    static final File techListFile = new File("ordered_gather_tech_list.jobj");
    static final File techMapFile = new File("gather_tech_map.jobj");
    static final int MIN_CLASS_CODE_LENGTH = 2;
    private static final double DECAY_RATE = 1.75;


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

    private static INDArray predictTechnologiesVectorForPatent(List<String> technologies, Map<String,INDArray> classProbabilities,  String patent, double decayRate) {
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

    public static void main(String[] args) throws Exception {
        Map<String,Collection<String>> gatherTechMap = Database.getGatherTechMap();
        List<String> orderedTechnologies = new ArrayList<>(gatherTechMap.keySet());

        // relevant cpc subgroup to patent map
        Collection<String> patentsToQuery = new HashSet<>();
        gatherTechMap.forEach((tech,patents)->patentsToQuery.addAll(patents));

        Collection<Patent> allPatents=PatentAPIHandler.requestAllPatents(patentsToQuery);
        System.out.println("Finished loading data");
        Map<String,Collection<String>> allCPCsToPatentsMap = new HashMap<>();
        allPatents.forEach(patent->{
            patent.getClassCodes().forEach(cpc->{
                if(cpc.getSubgroup()==null)return;
                String cpcStr = ClassCodeHandler.convertToLabelFormat(cpc.getSubgroup()).trim();
                while(cpcStr.length()>=MIN_CLASS_CODE_LENGTH) {
                    if(allCPCsToPatentsMap.containsKey(cpcStr)) {
                        allCPCsToPatentsMap.get(cpcStr).add(patent.getPatentNumber());
                    } else {
                        Set<String> set = new HashSet<>();
                        set.add(patent.getPatentNumber());
                        allCPCsToPatentsMap.put(cpcStr,set);
                    }
                    cpcStr=cpcStr.substring(0,cpcStr.length()-1).trim();
                }
            });
        });
        System.out.println("Finished adding cpcs to map");
        Collection<String> allClassCodes = new HashSet<>(allCPCsToPatentsMap.keySet());

        Map<String,INDArray> classCodeToCondProbMap = new HashMap<>();

        handleCPCS(allClassCodes,orderedTechnologies,gatherTechMap,allCPCsToPatentsMap,classCodeToCondProbMap);

        System.out.println("Finished handling cpcs...");

        // get values for each patent and assignee
        Collection<String> patents = Database.getCopyOfAllPatents();
        Map<String,INDArray> map = new HashMap<>();
        patents.forEach(patent->{
           INDArray probVec = predictTechnologiesVectorForPatent(orderedTechnologies,classCodeToCondProbMap,patent,DECAY_RATE);
           if(probVec!=null) {
               map.put(patent,probVec);
           }
        });


        // add assignees
        Map<String,INDArray> assigneeMap = new HashMap<>();
        Database.getAssignees().forEach(assignee->{
           Collection<String> assigneePatents = new HashSet<>();
           assigneePatents.addAll(Database.selectPatentNumbersFromAssignee(assignee));
           if(assigneePatents.isEmpty()) return;
           List<String> patentList=assigneePatents.stream().filter(patent->map.containsKey(patent)).collect(Collectors.toList());
           if(patentList.isEmpty()) return;
           INDArray avg = Nd4j.create(patentList.size(),orderedTechnologies.size());
           for(int i = 0; i < patentList.size(); i++) {
               avg.putRow(i,map.get(patentList.get(i)).dup());
           }
           assigneeMap.put(assignee,avg.mean(0));
           System.out.println("Added probabilities for assignee: "+assignee);
        });

        map.putAll(assigneeMap);

        // Adding CPCS to MAP
        System.out.println("Added assignees; now adding CPCs");
        map.putAll(classCodeToCondProbMap);

        Database.trySaveObject(map,techMapFile);
        // save ordered list too
        Database.trySaveObject(orderedTechnologies,techListFile);
    }


}
