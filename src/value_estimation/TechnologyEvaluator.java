package value_estimation;

import analysis.Patent;
import analysis.SimilarPatentFinder;
import dl4j_neural_nets.vectorization.ParagraphVectorModel;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class TechnologyEvaluator extends Evaluator {
    static final File file = new File("classification_value_model.jobj");
    public TechnologyEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Technology Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(file),
                (Map<String,Double>)Database.tryLoadObject(PriorArtEvaluator.file));
    }

    private static Map<String,Double> runModel(ParagraphVectors paragraphVectors){
        System.out.println("Starting to load classification evaluator...");
        Map<LocalDate,Set<String>> dateToPatentsMap = Collections.synchronizedMap(groupMapByMonth((Map<LocalDate,Set<String>>)Database.tryLoadObject(new File("pubdate_to_patent_map.jobj"))));
        SortedSet<LocalDate> sortedDates = new TreeSet<>(dateToPatentsMap.keySet());
        Collection<String> assignees = Database.getAssignees();
        WeightLookupTable<VocabWord> lookupTable = paragraphVectors.getLookupTable();
        SimilarPatentFinder classCodeFinder = new SimilarPatentFinder(Database.getClassCodes(),null,lookupTable);

        List<INDArray> data = Collections.synchronizedList(new ArrayList<>());

        for(LocalDate date : sortedDates) {
            System.out.println("Starting date: "+date);
            List<String> patentSample = new ArrayList<>(dateToPatentsMap.get(date));
            SimilarPatentFinder sampleFinder = new SimilarPatentFinder(patentSample,null,lookupTable);
            INDArray avg = sampleFinder.computeAvg();
            if(avg!=null) {
                data.add(avg);
            }
        }

        System.out.println("Calculating analysis for evaluator...");
        // compute scores for each patent
        Map<String,Double> model = new HashMap<>();
        classCodeFinder.getPatentList().forEach(cpcCode->{
            double totalScore = 0.0;
            for(int i = 0; i < data.size()-1; i+=2) {
                double simT = Transforms.cosineSim(cpcCode.getVector(),data.get(i))+1.0;
                double simT2 = Transforms.cosineSim(cpcCode.getVector(),data.get(i+1))+1.0;
                // calculate growth
                double growth = (simT2-simT)/simT;
                double timeFactor = Math.pow((double)i,1.5);
                totalScore+=(timeFactor*growth);
            }
            System.out.println("Score for class "+cpcCode.getName()+": "+totalScore);
            model.put(cpcCode.getName(),totalScore);
        });

        System.out.println("Now calculating scores for patents...");
        SimilarPatentFinder patentFinder = new SimilarPatentFinder(Database.getValuablePatents(),null,lookupTable);
        // compute scores for each patent
        patentFinder.getPatentList().forEach(patent->{
            double totalScore = 0.0;
            for(int i = 0; i < data.size()-1; i+=2) {
                double simT = Transforms.cosineSim(patent.getVector(),data.get(i))+1.0;
                double simT2 = Transforms.cosineSim(patent.getVector(),data.get(i+1))+1.0;
                // calculate growth
                double growth = (simT2-simT)/simT;
                double timeFactor = Math.pow((double)i,1.5);
                totalScore+=(timeFactor*growth);
            }
            // combine with actual classification scores
            List<Double> toAvg = new ArrayList<>();
            Database.classificationsFor(patent.getName()).forEach(cpcClass->{
                if(model.containsKey(cpcClass)) {
                    toAvg.add(model.get(cpcClass));
                }
            });
            if(!toAvg.isEmpty()) {
                totalScore=0.5*(totalScore+toAvg.stream().collect(Collectors.averagingDouble(s->s)));
            }
            System.out.println("Score for patent "+patent.getName()+": "+totalScore);
            model.put(patent.getName(),totalScore);
        });

        addScoresToAssigneesFromPatents(assignees,model,lookupTable);

        System.out.println("Removing class codes to save space");
        // for now remove class codes
        classCodeFinder.getPatentList().forEach(classCode->{
            if(model.containsKey(classCode.getName())) model.remove(classCode.getName());
        });

        System.out.println("Finished evaluator...");
        return model;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to load lookupTable...");
        ParagraphVectors paragraphVectors = ParagraphVectorModel.loadParagraphsModel();
        System.out.println("Finished.");
        Map<String,Double> map = runModel(paragraphVectors);
        ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        oos.writeObject(map);
        oos.flush();
        oos.close();
    }
}
