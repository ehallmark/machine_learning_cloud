package models.value_models.regression;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.File;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class CitationEvaluator extends ValueAttr {
    static final File file = new File("data/citation_value_model.jobj");

    @Override
    public String getName() {
        return Constants.CITATION_VALUE;
    }

    protected static Map<String,Double> runModel(){
        System.out.println("Starting to load citation evaluator...");
        List<String> patents = new ArrayList<>(((Map<String,Set<String>>) Database.tryLoadObject(new File("patent_to_cited_patents_map.jobj"))).keySet());
        Map<String,Set<String>> patentToReferencedByMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_referenced_by_map.jobj"));
        Map<String,Collection<String>> patentToCitationsMap = Database.getPatentToCitedPatentsMap();
        Map<String,LocalDate> patentToDateMap = Database.getPatentToPubDateMap();

        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for patents...");
        Map<String,Double> oldScores = new HashMap<>();
        LocalDate earliestDate = LocalDate.now().minusYears(20);
        double beginningTrendValue = (new Double(earliestDate.getYear())+new Double(earliestDate.getMonthValue()-1)/12.0);
        // Get score estimate
        patents.forEach(patent->{
            double score = 0.0;
            if(patentToReferencedByMap.containsKey(patent)) {
                score+=(double)(patentToReferencedByMap.get(patent).size());
            } else {
                score+=1.0;
            }
            if(patentToCitationsMap.containsKey(patent)) {
                score+=Math.log(1.0+patentToCitationsMap.get(patent).size());
            }
            if(patentToDateMap.containsKey(patent)) {
                LocalDate date = patentToDateMap.get(patent);
                double trend = (new Double(date.getYear())+new Double(date.getMonthValue()-1)/12.0)-beginningTrendValue;
                score+=0.1*Math.pow(trend,2.0);
            }
            oldScores.put(patent,score);
        });
        System.out.println("Updating scores again...");
        patents.forEach(patent->{
            double totalScore = 0.0;
            if(patentToReferencedByMap.containsKey(patent)) {
                double score = oldScores.get(patent);
                Set<String> references = patentToReferencedByMap.get(patent);
                double bonus = 0.0;
                for(String ref : references) {
                    double tmp = 0.0;
                    if (oldScores.containsKey(ref)) {
                        tmp = oldScores.get(ref);
                    } else {
                        tmp = 1.0;
                    }
                    bonus += tmp;
                }

                totalScore += Math.max(1.0, score + bonus);
            }
            if(patentToCitationsMap.containsKey(patent)) {
                Collection<String> citations = patentToCitationsMap.get(patent);
                for(String citation : citations) {
                    if(oldScores.containsKey(citation)) {
                        totalScore-=Math.log(oldScores.get(citation));
                    }
                }
            }
            System.out.println("Score for patent "+patent+": "+totalScore);
            model.put(patent,totalScore);

        });

        System.out.println("Finished evaluator...");
        return model;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<String,Double> map = runModel();
        System.out.println("Finished... Now writing model to file...");
        Database.trySaveObject(map,file);
    }
}
