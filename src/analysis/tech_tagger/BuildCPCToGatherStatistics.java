package analysis.tech_tagger;

import analysis.patent_view_api.Patent;
import analysis.patent_view_api.PatentAPIHandler;
import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import tools.ClassCodeHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 2/18/2017.
 */
public class BuildCPCToGatherStatistics {
    static final File file = new File("cpc_to_gather_tech_mapping_trie.jobj");
    static final File techListFile = new File("gather_tech_list.jobj");
    static final int MIN_CLASS_CODE_LENGTH = 4;

    // compute probability of T given C
    public static void handleLayer(Collection<String> cpcGroup, List<String> orderedTechnologies, Map<String,Collection<String>> gatherTechMap, Map<String,Collection<String>> allCPCsToPatentsMap, RadixTree<INDArray> classCodeToCondProbMap) {
        cpcGroup.forEach(cpc->{
            Collection<String> patentsInClass = allCPCsToPatentsMap.get(cpc);
            int classSize = patentsInClass.size();
            System.out.println("Num patents for "+cpc+": "+patentsInClass.size());
            // go through technologies
            INDArray probabilityVector = Nd4j.create(orderedTechnologies.size());
            for(int i = 0; i < orderedTechnologies.size(); i++) {
                String tech = orderedTechnologies.get(i);
                Collection<String> patentsInTech = gatherTechMap.get(tech);
                AtomicInteger cnt = new AtomicInteger(0);
                patentsInTech.forEach(p->{
                    if(patentsInClass.contains(p)) {
                        cnt.getAndIncrement();
                    }
                });
                double probability = new Double(cnt.get())/(classSize);
                probabilityVector.putScalar(i,probability);
            }
            classCodeToCondProbMap.put(cpc,probabilityVector);
        });
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
        // sort by length
        Map<Integer,Collection<String>> lengthCPCToClassCodeMap = new HashMap<>();
        allClassCodes.forEach(cpc->{
            if(lengthCPCToClassCodeMap.containsKey(cpc.length())) {
                lengthCPCToClassCodeMap.get(cpc.length()).add(cpc);
            } else {
                Set<String> set = new HashSet<>();
                set.add(cpc);
                lengthCPCToClassCodeMap.put(cpc.length(),set);
            }
        });

        RadixTree<INDArray> classCodeToCondProbMap = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());

        lengthCPCToClassCodeMap.forEach((size,cpcGroup)->{
            System.out.println("Starting layer "+size+" with "+cpcGroup.size()+" patents");
            handleLayer(cpcGroup,orderedTechnologies,gatherTechMap,allCPCsToPatentsMap,classCodeToCondProbMap);
        });

        Database.trySaveObject(classCodeToCondProbMap,file);
        Database.trySaveObject(orderedTechnologies,techListFile);
    }
}
