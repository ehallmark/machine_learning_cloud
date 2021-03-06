package models.similarity_models.combined_similarity_model;

import models.similarity_models.deep_cpc_encoding_model.DeepCPCVAEPipelineManager;
import org.nd4j.linalg.primitives.Pair;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;

import java.util.*;
import java.util.stream.Collectors;

public class TestAssigneeModels extends TestModelHelper {
    private static Map<String,List<String>> getAssigneeToFilingMap(List<String> assignees) {
        Map<String,Collection<String>> assigneeToPatentMap = new AssigneeToAssetsMap().getPatentDataMap();
        Map<String,String> patentToFilingMap = new AssetToFilingMap().getPatentDataMap();

        return assignees.parallelStream().map(assignee->{
            Collection<String> assets = assigneeToPatentMap.get(assignee);
            if(assets==null)return null;
            return new Pair<>(assignee,assets.stream().map(asset->patentToFilingMap.get(asset)).filter(asset->asset!=null).collect(Collectors.toList()));
        }).filter(p->p!=null&&p.getSecond().size()>0)
                .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));
    }

    private static Map<String,Pair<String,String>> loadData(int n) {
        List<String> assignees = new ArrayList<>(Database.getAssignees());
        Collections.shuffle(assignees, new Random(2352));
        assignees = assignees.subList(0,Math.min(n,assignees.size()));
        Random rand = new Random(1351);

        Map<String,List<String>> assigneeToFilingMap = getAssigneeToFilingMap(assignees);

        Map<String,Pair<String,String>> data = Collections.synchronizedMap(new HashMap<>(n));

        List<String> allFilings = assigneeToFilingMap.values().parallelStream().flatMap(s->s.stream()).distinct().collect(Collectors.toList());

        assignees.parallelStream().forEach(assignee->{
            List<String> filings = assigneeToFilingMap.get(assignee);
            if(filings==null) return;
            String pos = filings.get(rand.nextInt(filings.size()));
            String neg = null;
            while(neg==null) {
                neg = allFilings.get(rand.nextInt(allFilings.size()));
                if(filings.contains(neg)) {
                    neg = null;
                }
            }
            data.put(assignee, new Pair<>(pos,neg));
        });

        return data;
    }

    public static void runTest(Tester tester) {
        String testName = "Assignee Test";
        // load input data
        final int numSamples = 100000;
        Map<String,Pair<String,String>> data = loadData(numSamples);

        double randomScore = testModel(data,RANDOM_MODEL);
        System.out.println("Random score: "+randomScore);
        tester.scoreModel("Random",testName,randomScore);
        // vae model
        DeepCPCVAEPipelineManager encodingPipelineManagerDeep = new DeepCPCVAEPipelineManager(DeepCPCVAEPipelineManager.MODEL_NAME);

        //DeeperCPCVAEPipelineManager encodingPipelineManagerDeeper = new DeeperCPCVAEPipelineManager(DeeperCPCVAEPipelineManager.MODEL_NAME);

        // run tests
        double deepScore = test(encodingPipelineManagerDeep, "Deep Model",data);
        //double deeperScore = test(encodingPipelineManagerDeeper, "Deeper Model",data);
        tester.scoreModel("Deep Model",testName,deepScore);
       // tester.scoreModel("Deeper Model",testName,deeperScore);
        System.out.println();
        System.out.println("Num samples used: "+data.size());
        System.out.println("Score for Deep Model: "+deepScore);
       // System.out.println("Score for Deeper Model: "+deeperScore);
    }
}
