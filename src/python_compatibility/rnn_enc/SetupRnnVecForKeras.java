package python_compatibility.rnn_enc;

import org.deeplearning4j.models.word2vec.Word2Vec;
import seeding.Database;
import seeding.google.postgres.Util;
import seeding.google.word2vec.Word2VecManager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SetupRnnVecForKeras {

    public static void main(String[] args) throws Exception {
        final File x1File = new File("/home/ehallmark/Downloads/rnn_keras_x1.csv");
        final File x2File = new File("/home/ehallmark/Downloads/rnn_keras_x2.csv");
        final File yFile = new File("/home/ehallmark/Downloads/rnn_keras_y.csv");
        final Random random = new Random(211);
        final int negativeSamples = 4;
        final int limit = 2000000;
        final int maxLen = 256;

        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        int paddingIdx = word2Vec.getVocab().numWords();
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select abstract from big_query_patent_english_abstract as p tablesample system(2) limit "+limit);
        ResultSet rs = ps.executeQuery();

        BufferedWriter x1 = new BufferedWriter(new FileWriter(x1File));
        BufferedWriter x2 = new BufferedWriter(new FileWriter(x2File));
        BufferedWriter y = new BufferedWriter(new FileWriter(yFile));

        List<String[]> samples = new ArrayList<>(limit);
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            String[] words = Util.textToWordFunction.apply(rs.getString(1));
            if(words==null || words.length>0) {
                samples.add(words);
            }

            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Loaded text for: "+cnt.get());
            }
        }

        rs.close();
        ps.close();
        conn.close();

        //Collections.shuffle(samples);
        cnt.set(0);
        for(int s = 0; s < samples.size(); s++) {
            String[] words = samples.get(s);
            // write identity
            int[] indices = Stream.of(words).mapToInt(word -> word2Vec.indexOf(word))
                    .filter(i -> i >= 0).limit(maxLen).toArray();
            writeToFile(maxLen, paddingIdx, x1, x2, y, indices, indices, 1);

            // create negative samples
            for(int neg = 0; neg < negativeSamples; neg++) {
                int randIdx = -1;
                while (randIdx < 0 || randIdx == s) {
                    randIdx = random.nextInt(samples.size());
                }
                String[] words2 = samples.get(randIdx);
                int[] indices2 = Stream.of(words2).mapToInt(word -> word2Vec.indexOf(word))
                        .limit(maxLen)
                        .filter(i -> i >= 0).toArray();
                if(random.nextBoolean()) { // randomly switch x1 and x2
                    writeToFile(maxLen, paddingIdx, x1, x2, y, indices, indices2, 0);
                } else {
                    writeToFile(maxLen, paddingIdx, x1, x2, y, indices2, indices, 0);
                }
            }
            if(cnt.getAndIncrement()%1000==999) {
                System.out.println("Saved results for: "+cnt.get());
            }
        }

        x1.flush();
        x1.close();

        x2.flush();
        x2.close();

        y.flush();
        y.close();

    }


    private static void writeToFile(int maxLen, int padIdx, BufferedWriter x1, BufferedWriter x2, BufferedWriter y, int[] indices, int[] indices2, int label) throws IOException {
        if(indices.length>0 && indices2.length>0) {
            String indicesStr = String.join(",", IntStream.of(indices).mapToObj(i->String.valueOf(i)).collect(Collectors.toList()));
            String indicesStr2 = String.join(",", IntStream.of(indices2).mapToObj(i->String.valueOf(i)).collect(Collectors.toList()));
            x1.write(indicesStr);
            x2.write(indicesStr2);
            for(int i = indices.length; i < maxLen; i++) {
                x1.write(","+padIdx);
            }
            for(int i = indices2.length; i < maxLen; i++) {
                x2.write(","+padIdx);
            }
            x1.write("\n");
            x2.write("\n");
            y.write(String.valueOf(label)+"\n");
        }
    }
}
