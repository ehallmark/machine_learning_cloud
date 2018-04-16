package seeding.google.tech_tag;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.Database;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class PredictTechTags {
    public static final File matrixFile = new File("tech_tag_statistics_matrix.jobj");
    public static final File titleListFile = new File("tech_tag_statistics_title_list.jobj");
    public static final File wordListFile = new File("tech_tag_statistics_word_list.jobj");
    private static final Function<String,String[]> textToWordFunction = text -> {
        return text.toLowerCase().replaceAll("[^a-z ]"," ").split("\\s+");
    };
    public static void main(String[] args) throws Exception {
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

        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            String familyId = rs.getString(1);
            String publicationNumber = rs.getString(2);
            String text = rs.getString(3);
            String[] content = textToWordFunction.apply(text);
            int found = 0;
            float[] data = new float[matrix.columns()];
            for(String word : content) {
                Integer idx = wordToIndexMap.get(word);
                if(idx!=null) {
                    found++;
                    data[idx]++;
                }
            }
            if(found>3) {
                INDArray vec = Transforms.unitVec(Nd4j.create(data)).reshape(matrix.columns(),1);
                INDArray scores = matrix.mmul(vec);
                int bestIdx = Nd4j.argMax(scores,0).getInt(0);
                String tag = allTitlesList.get(bestIdx);
                System.out.println("Best tag for "+publicationNumber+": "+tag);
            }
        }
    }
}
