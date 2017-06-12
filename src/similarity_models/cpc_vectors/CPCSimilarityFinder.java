package similarity_models.cpc_vectors;

import dl4j_neural_nets.vectorization.auto_encoders.CPCVariationalAutoEncoderModel;
import graphical_models.classification.CPCKMeans;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Constants;
import seeding.Database;
import similarity_models.BaseSimilarityModel;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Evan on 6/8/2017.
 */
public class CPCSimilarityFinder extends BaseSimilarityModel {
    private static final File file = new File("data/cpc_similarity_finder_lookup_table.jobj");
    private static Map<String,INDArray> LOOKUP_TABLE;
    public CPCSimilarityFinder(Collection<String> candidateSet, String name) {
        super(candidateSet,name,getLookupTable());
    }

    public static Map<String,INDArray> getLookupTable() {
        if(LOOKUP_TABLE==null) {
            LOOKUP_TABLE=(Map<String,INDArray>) Database.tryLoadObject(file);
        }
        return LOOKUP_TABLE;
    }
    public static void main(String[] args) throws IOException {
        Database.initializeDatabase();

        int cpcDepth = CPCKMeans.DEFAULT_CPC_DEPTH;
        List<String> orderedClassifications = CPCVariationalAutoEncoderModel.getOrderedClassifications();
        MultiLayerNetwork model = CPCVariationalAutoEncoderModel.getModel();
        Map<String,INDArray> toSave = Collections.synchronizedMap(new HashMap<>());

        List<Pair<String,List<String>>> collections = Collections.synchronizedList(new ArrayList<>());

        Database.getCopyOfAllPatents().parallelStream().forEach(patent->{
            collections.add(new Pair<>(patent,Arrays.asList(patent)));
        });

        System.out.println("Starting assignees");
        Database.getAssignees().parallelStream().forEach(assignee->{
            List assets = new ArrayList(Database.selectPatentNumbersFromExactAssignee(assignee));
            if(assets==null||assets.isEmpty()) return;
            collections.add(new Pair<>(assignee,assets));
        });

        int batchSize = 10000;
        System.out.println("Batching...");
        List<Pair<List<String>,INDArray>> data = new ArrayList<>();
        for(int i = 0; i < collections.size(); i+=batchSize) {
            List<INDArray> list = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for(int j = i; j < Math.min(i+batchSize,collections.size()-1); j++) {
                names.add(collections.get(j).getFirst());
                list.add(Nd4j.create(CPCKMeans.classVectorForPatents(collections.get(j).getSecond(),orderedClassifications,cpcDepth)));
            }
            data.add(new Pair<>(names,Nd4j.vstack(list)));
            System.out.println("i: "+i);
        }

        System.out.println("Adding to map");
        data.parallelStream().forEach(pair->{
            INDArray vec = pair.getSecond();
            List<String> names = pair.getFirst();
            if(vec!=null) {
                // encode
                INDArray encoding = model.getLayer(0).activate(vec,false);
                if(encoding!=null) {
                    for(int i = 0; i < names.size(); i++) {
                        toSave.put(names.get(i), encoding.getRow(i));
                    }
                }
            }
        });

        Database.trySaveObject(toSave,file);
    }
}
