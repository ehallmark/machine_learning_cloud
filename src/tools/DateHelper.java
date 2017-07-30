package tools;

import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 5/9/2017.
 */
public class DateHelper {
    public static Map<LocalDate,Set<String>> groupMapByMonth(Map<LocalDate,Set<String>> groupedByDayMap) {
        // group dates and patents by month
        Map<LocalDate,Set<String>> groupedDateToPatentMap = new HashMap<>();
        groupedByDayMap.forEach((date,assets)->{
            List<String> assetList = new ArrayList<>(assets);
            Collections.shuffle(assetList);
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

    public static void addScoresToAssigneesFromPatents(Collection<String> assignees, Map<String,Double> model) {
        System.out.println("Adding scores to assignees...");
        (assignees.stream()).parallel().forEach(assignee->{
            Collection<String> assigneePatents = Arrays.asList(Database.selectPatentNumbersFromAssignee(assignee),Database.selectApplicationNumbersFromAssignee(assignee)).stream()
                    .flatMap(list->list.stream()).collect(Collectors.toList());
            double score = 0.0;
            int toDivide = 0;
            for (String patent : assigneePatents) {
                if(!model.containsKey(patent)) continue;
                score += model.get(patent);
                toDivide += 1;
            }
            if(toDivide>0) {
                score = score/toDivide;
                model.put(assignee,score);
            }
        });
        System.out.println("Finished assignees.");
    }

}
