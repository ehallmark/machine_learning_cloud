package value_estimation;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;

import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public abstract class Evaluator {
    // Instance class
    protected Map<String,Double> model;

    public Evaluator(ValueMapNormalizer.DistributionType distributionType) {
        this.model=loadModel();
        new ValueMapNormalizer(distributionType).normalizeToRange(model,1.0,5.0);
    }

    public static Map<LocalDate,Set<String>> groupMapByMonth(Map<LocalDate,Set<String>> groupedByDayMap, int samples) {
        // group dates and patents by month
        Map<LocalDate,Set<String>> groupedDateToPatentMap = new HashMap<>();
        groupedByDayMap.forEach((date,assets)->{
            List<String> assetList = new ArrayList<>(assets);
            Collections.shuffle(assetList);
            assetList=assetList.subList(0,Math.max(assetList.size(),samples));
            LocalDate firstDayOfMonth =LocalDate.of(date.getYear(),date.getMonthValue(),1);
            if(groupedDateToPatentMap.containsKey(firstDayOfMonth)) {
                groupedDateToPatentMap.get(firstDayOfMonth).addAll(assetList);
            } else {
                Set<String> set = new HashSet<>(assetList);
                groupedDateToPatentMap.put(firstDayOfMonth,set);
            }
        });
        return groupedDateToPatentMap;
    }

    public static void addScoresToAssigneesFromPatents(Collection<String> assignees, Map<String,Double> model, WeightLookupTable<VocabWord> lookupTable) {
        System.out.println("Adding scores to assignees...");
        assignees.forEach(assignee->{
            Collection<String> assigneePatents = Database.selectPatentNumbersFromAssignee(assignee);
            double score = 0.0;
            double toDivide = 0.0;
            INDArray assigneeVec = lookupTable.vector(assignee);
            if(assigneeVec!=null) {
                for (String patent : assigneePatents) {
                    if(!model.containsKey(patent)) continue;
                    INDArray patentVec = lookupTable.vector(patent);
                    if (patentVec != null) {
                        double weight = Math.max(0.2, Transforms.cosineSim(patentVec, assigneeVec));
                        score += model.get(patent) * weight;
                        toDivide += weight;
                    }
                }
            }
            if(toDivide>0) {
                score = score/toDivide;
            } else score=0.0;
            model.put(assignee,score);
        });
        System.out.println("Finished assignees.");
    }

    protected abstract Map<String,Double> loadModel();

    public Map<String,Double> getMap() {
        return model;
    }

    // Returns value between 1 and 5
    public double evaluate(String token) {
        if(model.containsKey(token)) {
            return model.get(token);
        } else {
            return 1.0;
        }
    }
}
