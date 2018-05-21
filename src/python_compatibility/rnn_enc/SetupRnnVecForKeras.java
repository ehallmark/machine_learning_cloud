package python_compatibility.rnn_enc;

import graphical_modeling.util.Pair;
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
        final int limit = 4000000;
        final int maxLen = 96;

        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();
        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select abstract from big_query_patent_english_abstract as p tablesample system(15) limit "+limit);
        ResultSet rs = ps.executeQuery();

        BufferedWriter x1 = new BufferedWriter(new FileWriter(x1File));
        BufferedWriter x2 = new BufferedWriter(new FileWriter(x2File));
        BufferedWriter y = new BufferedWriter(new FileWriter(yFile));

        List<int[]> samples = new ArrayList<>(limit);
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            String[] words = Util.textToWordFunction.apply(rs.getString(1));
            if(words==null || words.length>0) {
                int[] indices = Stream.of(words).mapToInt(word -> word2Vec.indexOf(word))
                        .filter(i -> i >= 0).limit(maxLen * 2).toArray();
                if(indices.length>3) {
                    samples.add(indices);
                }
            }

            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Loaded text for: "+cnt.get());
            }
        }

        rs.close();
        ps.close();
        conn.close();

        System.out.println("Shuffling...");
        Collections.shuffle(samples);
        System.out.println("Finished shuffling.");

        cnt.set(0);
        for(int s = 0; s < samples.size(); s++) {
            // write identity
            Pair<int[],int[]> sample = splitIndices(samples.get(s), maxLen, random);
            int[] idx1 = sample._1;
            int[] idx2 = sample._2;
            int[] indices;
            if(random.nextBoolean()) {
                indices=idx1;
                writeToFile(maxLen, x1, x2, y, idx1, idx2, 1);
            } else {
                indices=idx2;
                writeToFile(maxLen, x1, x2, y, idx2, idx1, 1);
            }
            // create negative samples
            for(int neg = 0; neg < negativeSamples; neg++) {
                int randIdx = -1;
                while (randIdx < 0 || randIdx == s) {
                    randIdx = random.nextInt(samples.size());
                }
                Pair<int[],int[]> possible = splitIndices(samples.get(randIdx), maxLen, random);
                int[] indices2 = possible._1;
                if(random.nextBoolean()) { // randomly switch x1 and x2
                    writeToFile(maxLen, x1, x2, y, indices, indices2, 0);
                } else {
                    writeToFile(maxLen, x1, x2, y, indices2, indices, 0);
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

    private static Pair<int[],int[]> splitIndices(int[] indices, int maxLen, Random random) {
        int[] ret1 = new int[maxLen];
        int[] ret2 = new int[maxLen];
        Arrays.fill(ret1,-1);
        Arrays.fill(ret2, -1);
        int[] idx1 = Arrays.copyOfRange(indices, 0, indices.length/2);
        int[] idx2 = Arrays.copyOfRange(indices, indices.length/2, indices.length);
        int st = random.nextInt(Math.max(1,idx1.length-maxLen));
        int end = Math.min(idx1.length-1,st+maxLen);
        idx1 = Arrays.copyOfRange(idx1, st, end);
        st = random.nextInt(Math.max(1,idx2.length-maxLen));
        end = Math.min(idx2.length-1,st+maxLen);
        idx2 = Arrays.copyOfRange(idx2, st, end);
        int idx = 0;
        for (int i = maxLen - idx1.length; i < maxLen; i++) {
            ret1[i] = idx1[idx];
            idx++;
        }
        idx = 0;
        for (int i = maxLen - idx2.length; i < maxLen; i++) {
            ret2[i] = idx2[idx];
            idx++;
        }
        if(random.nextBoolean()) {
            return new Pair<>(ret1,ret2);
        } else {
            return new Pair<>(ret2,ret1);
        }
    }

    private static void writeToFile(int maxLen, BufferedWriter x1, BufferedWriter x2, BufferedWriter y, int[] indices, int[] indices2, int label) throws IOException {
        if(indices.length>0 && indices2.length>0) {
            String indicesStr = String.join(",", IntStream.of(indices).mapToObj(i->String.valueOf(i+1)).limit(maxLen).collect(Collectors.toList()));
            String indicesStr2 = String.join(",", IntStream.of(indices2).mapToObj(i->String.valueOf(i+1)).limit(maxLen).collect(Collectors.toList()));
            x1.write(indicesStr);
            x2.write(indicesStr2);
            for(int i = indices.length; i < maxLen; i++) {
                x1.write(",0");
            }
            for(int i = indices2.length; i < maxLen; i++) {
                x2.write(",0");
            }
            x1.write("\n");
            x2.write("\n");
            y.write(String.valueOf(label)+"\n");
        }
    }
}
