package value_estimation;

import analysis.SimilarPatentFinder;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
import org.apache.commons.math3.distribution.ExponentialDistribution;
import org.deeplearning4j.models.embeddings.WeightLookupTable;
import org.deeplearning4j.models.paragraphvectors.ParagraphVectors;
import org.deeplearning4j.models.word2vec.VocabWord;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;
import server.tools.excel.ExcelWritable;
import tools.PortfolioList;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class CitationEvaluator extends Evaluator {
    private static final File file = new File("citation_value_model.jobj");

    public CitationEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal);
    }

    @Override
    protected Map<String,Double> loadModel() {
        return (Map<String,Double>)Database.tryLoadObject(file);
    }

    private static Map<String,Double> runModel(){
        System.out.println("Starting to load citation evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();
        Map<String,Set<String>> patentToReferencedByMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_referenced_by_map.jobj"));
        Map<String,Set<String>> patentToCitationsMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_cited_patents_map.jobj"));
        Map<String,LocalDate> patentToDateMap = (Map<String,LocalDate>)Database.tryLoadObject(new File("patent_to_pubdate_map_file.jobj"));

        Map<String,Double> model = new HashMap<>();
        System.out.println("Calculating scores for patents...");
        Map<String,Double> oldScores = new HashMap<>();
        LocalDate earliestDate = LocalDate.now().minusYears(5);
        double beginningTrendValue = (new Double(earliestDate.getYear())+new Double(earliestDate.getMonthValue()-1)/12.0);
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
                score+=trend/patentToDateMap.size();
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
                        tmp = Math.max(1.0,oldScores.get(ref));
                    } else {
                        tmp = 1.0;
                    }
                    bonus += tmp;
                }

                totalScore += Math.max(1.0, score + bonus);
            }
            if(patentToCitationsMap.containsKey(patent)) {
                for(String citation : patentToCitationsMap.get(patent)) {
                    if(oldScores.containsKey(citation)) {
                        totalScore+=Math.max(-1.0,oldScores.get(citation));
                    }
                }
            }
            System.out.println("Score for patent "+patent+": "+totalScore);
            model.put(patent,totalScore);

        });
        try {
            addScoresToAssigneesFromPatents(assignees, model, ParagraphVectorModel.loadParagraphsModel().getLookupTable());
        } catch(Exception e) {
            e.printStackTrace();
        }
        System.out.println("Finished evaluator...");
        return model;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<String,Double> map = runModel();
        System.out.println("Finished... Now writing model to file...");
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(map);
        oos.flush();
        oos.close();
        System.out.println("Finished successfully.");
    }
}
