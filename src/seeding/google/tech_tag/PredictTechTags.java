package seeding.google.tech_tag;

import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class PredictTechTags {
    public static final File matrixFile = new File("tech_tag_statistics_matrix.jobj");
    public static final File titleListFile = new File("tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("tech_tag_statistics_word_list.jobj");
    private static final Function<String,String[]> textToWordFunction = text -> {
        return text.toLowerCase().replaceAll("[^a-z ]"," ").split("\\s+");
    };
    public static void main(String[] args) throws Exception {
        Nd4j.setDataType(DataBuffer.Type.FLOAT);
        INDArray matrix = (INDArray) Database.tryLoadObject(matrixFile);
        List<String> allTitlesList = (List<String>) Database.tryLoadObject(titleListFile);
        List<String> allWordsList = (List<String>) Database.tryLoadObject(wordListFile);

        Map<String,Integer> wordToIndexMap = new HashMap<>();
        Map<String,Integer> titleToIndexMap = new HashMap<>();
        for(int i = 0; i < allWordsList.size(); i++) {
            wordToIndexMap.put(allWordsList.get(i),i);
        }
        for(int i = 0; i < allTitlesList.size(); i++) {
            titleToIndexMap.put(allTitlesList.get(i),i);
        }
        Connection seedConn = Database.newSeedConn();
        PreparedStatement ps = seedConn.prepareStatement("select family_id,publication_number_full,abstract from big_query_patent_english_abstract");
        ps.setFetchSize(10);
        ResultSet rs = ps.executeQuery();
        AtomicLong cnt = new AtomicLong(0);
        Connection conn = Database.getConn();
        final int batch = 100;
        while(true) {
            int i = 0;
            INDArray vectors = Nd4j.create(matrix.columns(),batch);
            List<String> familyIds = new ArrayList<>(batch);
            for(; i < batch&&rs.next(); i++) {
                String familyId = rs.getString(1);
                familyIds.add(familyId);
                String publicationNumber = rs.getString(2);
                String text = rs.getString(3);
                String[] content = textToWordFunction.apply(text);
                int found = 0;
                float[] data = new float[matrix.columns()];
                for (String word : content) {
                    Integer idx = wordToIndexMap.get(word);
                    if (idx != null) {
                        found++;
                        data[idx]++;
                    }
                }
                if (found > 3) {
                    vectors.putColumn(i, Nd4j.create(data));
                    //System.out.println("Best tag for " + publicationNumber + ": " + tag);
                } else {
                    i--;
                    continue;
                }
                if (cnt.getAndIncrement() % 100 == 99) {
                    System.out.println("Finished: " + cnt.get());
                }
            }
            if(i==0) {
                break;
            }
            if(i<batch) {
                vectors = vectors.get(NDArrayIndex.all(),NDArrayIndex.interval(0,i));
            }
            vectors.diviRowVector(vectors.norm2(0));
            INDArray scores = matrix.mmul(vectors);
            int[] bestIndices = Nd4j.argMax(scores, 0).data().asInt();
            String insert = "insert into big_query_technologies (family_id,technology) values ? on conflict(family_id) do update set technology=excluded.technology";
            StringJoiner valueJoiner = new StringJoiner(",");
            StringJoiner innerJoiner = new StringJoiner("\",\"","(\"","\")");
            for(int j = 0; j < i; j++) {
                int bestIdx = bestIndices[j];
                String familyId = familyIds.get(j);
                String tag = allTitlesList.get(bestIdx);
                innerJoiner.add(familyId).add(tag);
                valueJoiner.add(innerJoiner.toString());
            }
            PreparedStatement insertPs = conn.prepareStatement(insert.replace("?",valueJoiner.toString()));
            insertPs.executeUpdate();
            if(cnt.get()%10000==9999) {
                Database.commit();
            }
        }

        Database.commit();
        seedConn.commit();
        Database.close();
    }
}
