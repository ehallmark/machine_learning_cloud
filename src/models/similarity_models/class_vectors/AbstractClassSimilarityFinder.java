package models.similarity_models.class_vectors;

import org.deeplearning4j.berkeley.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import seeding.Database;
import models.similarity_models.class_vectors.vectorizer.ClassVectorizer;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Created by Evan on 6/8/2017.
 */
public abstract class AbstractClassSimilarityFinder {

    public static void trainAndSave(Map<String,? extends Collection<String>> dataMap, int classDepth, File file) throws IOException {
        ClassVectorizer vectorizer = new ClassVectorizer(dataMap);
        List<String> orderedClassifications = vectorizer.getClassifications(Database.getCopyOfAllPatents(),classDepth,true);
        Map<String,INDArray> lookupTable = Collections.synchronizedMap(new HashMap<>());

        // Batching
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
                list.add(Nd4j.create(vectorizer.classVectorForPatents(collections.get(j).getSecond(),orderedClassifications,classDepth)));
            }
            data.add(new Pair<>(names,Nd4j.vstack(list)));
            System.out.println("i: "+i);
        }
        // Done batching
        System.out.println("Adding to map");
        data.parallelStream().forEach(pair->{
            INDArray vec = pair.getSecond();
            List<String> names = pair.getFirst();
            if(vec!=null) {
                // encode
                INDArray encoding = vec; //model.getLayer(0).activate(vec,false);
                if(encoding!=null) {
                    for(int i = 0; i < names.size(); i++) {
                        lookupTable.put(names.get(i), encoding.getRow(i));
                    }
                }
            }
        });

        Database.trySaveObject(lookupTable,file);
    }
}
