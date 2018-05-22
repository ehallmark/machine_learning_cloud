package python_compatibility.rnn_enc;

import cpc_normalization.CPCHierarchy;
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

public class SetupWordCPCRnnForKeras {

    public static void main(String[] args) throws Exception {
        final File x1File = new File("/home/ehallmark/Downloads/word_cpc_rnn_keras_x1.csv");
        final File x2File = new File("/home/ehallmark/Downloads/word_cpc_rnn_keras_x2.csv");
        final File cpcFile = new File("/home/ehallmark/Downloads/word_cpc_rnn_keras_cpc.csv");
        final File yFile = new File("/home/ehallmark/Downloads/word_cpc_rnn_keras_y.csv");
        final Random random = new Random(211);
        final int negativeSamples = 4;
        final int maxLen = 128;

        Connection conn = Database.getConn();
        CPCHierarchy hierarchy = CPCHierarchy.get();
        List<String> allCPCs = new ArrayList<>(hierarchy.getLabelToCPCMap().keySet());
        Collections.sort(allCPCs);
        Map<String,Integer> cpcToIndexMap = new HashMap<>();
        for(int i = 0; i < allCPCs.size(); i++) {
            cpcToIndexMap.put(allCPCs.get(i), i);
        }
        Map<String,List<String>> pubToCPCMap = new HashMap<>();
        {
            PreparedStatement ps = conn.prepareStatement("select publication_number_full, tree from big_query_cpc_tree tablesample system (20)");
            ps.setFetchSize(1000);
            ResultSet rs = ps.executeQuery();
            int cnt = 0;
            while(rs.next()) {
                String pub = rs.getString(1);
                String[] cpcs = (String[]) rs.getArray(2).getArray();
                List<String> cpcList = Stream.of(cpcs).map(cpc->hierarchy.getLabelToCPCMap().get(cpc))
                        .filter(cpc->cpc!=null && cpc.getNumParts()>4)
                        .map(cpc->cpc.getName())
                        .collect(Collectors.toList());
                if(cpcList.size()>0) {
                    pubToCPCMap.put(pub, cpcList);
                }
                if(cnt % 10000==9999) {
                    System.out.println("Seen : "+cnt+" cpcs.");
                }
                cnt++;
            }
        }

        Word2Vec word2Vec = Word2VecManager.getOrLoadManager();

        PreparedStatement ps = conn.prepareStatement("select publication_number_full,abstract from big_query_patent_english_abstract as p");
        ps.setFetchSize(100);
        ResultSet rs = ps.executeQuery();

        BufferedWriter x1 = new BufferedWriter(new FileWriter(x1File));
        BufferedWriter cpc = new BufferedWriter(new FileWriter(cpcFile));
        BufferedWriter x2 = new BufferedWriter(new FileWriter(x2File));
        BufferedWriter y = new BufferedWriter(new FileWriter(yFile));

        List<int[]> samples = new ArrayList<>(4000000);
        Map<String, List<int[]>> cpcSampleMap = new HashMap<>();
        AtomicInteger cnt = new AtomicInteger(0);
        List<List<String>> cpcsByIndex = new ArrayList<>(4000000);
        while(rs.next()) {
            String pub = rs.getString(1);
            List<String> cpcList = pubToCPCMap.get(pub);
            if(cpcList==null) continue;

            String[] words = Util.textToWordFunction.apply(rs.getString(2));
            if(words!=null && words.length>0) {
                int[] indices = Stream.of(words).mapToInt(word -> word2Vec.indexOf(word))
                        .filter(i -> i >= 0).limit(5*maxLen).toArray();
                if(indices.length>3) {

                    if(cpcList.size()>0) {
                        samples.add(indices);
                        for(String c : cpcList) {
                            cpcSampleMap.putIfAbsent(c, new ArrayList<>());
                            cpcSampleMap.get(c).add(indices);
                        }
                        cpcsByIndex.add(cpcList);
                    }
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
        long seed = 10;
        Collections.shuffle(samples, new Random(seed));
        Collections.shuffle(cpcsByIndex, new Random(seed));
        System.out.println("Finished shuffling.");

        cnt.set(0);
        for(int s = 0; s < samples.size(); s++) {
            // write identity
            int[] indices = splitIndices(samples.get(s), maxLen, random);
            List<String> cpcs = cpcsByIndex.get(s);
            String c = cpcs.get(random.nextInt(cpcs.size()));
            int cpcIdx = cpcToIndexMap.get(c);
            List<int[]> cooccurring = new ArrayList<>(cpcSampleMap.get(c));
            cooccurring.remove(samples.get(s));
            if(cooccurring.size()>0) {
                int[] positive = splitIndices(cooccurring.get(random.nextInt(cooccurring.size())), maxLen, random);
                if(random.nextBoolean()) {
                    writeToFile(maxLen, x1, x2, cpc, y, indices, positive, cpcIdx,1);
                } else {
                    writeToFile(maxLen, x1, x2, cpc, y, positive, indices, cpcIdx,1);
                }
                // create negative samples
                for (int neg = 0; neg < negativeSamples; neg++) {
                    int randIdx = -1;
                    while (randIdx < 0 || randIdx == s) {
                        randIdx = random.nextInt(samples.size());
                    }
                    int[] negative = splitIndices(samples.get(randIdx), maxLen, random);
                    if (random.nextBoolean()) { // randomly switch x1 and x2
                        List<String> negCpcs = cpcsByIndex.get(randIdx);
                        String randCpc = negCpcs.get(random.nextInt(negCpcs.size()));
                        int randCpcIdx = cpcToIndexMap.get(randCpc);
                        writeToFile(maxLen, x1, x2, cpc, y, indices, negative, randCpcIdx,0);

                    } else {
                        writeToFile(maxLen, x1, x2, cpc, y, negative, indices, cpcIdx,0);
                    }
                }
                if (cnt.getAndIncrement() % 1000 == 999) {
                    System.out.println("Saved results for: " + cnt.get());
                }
            }
        }

        x1.flush();
        x1.close();

        x2.flush();
        x2.close();

        y.flush();
        y.close();

        cpc.flush();
        cpc.close();

    }

    private static int[] splitIndices(int[] indices, int maxLen, Random random) {
        int[] ret1 = new int[maxLen];
        Arrays.fill(ret1,-1);
        int[] idx1 = Arrays.copyOfRange(indices, 0, indices.length/2);
        int st = random.nextInt(Math.max(1,idx1.length-maxLen));
        int end = Math.min(idx1.length-1,st+maxLen);
        idx1 = Arrays.copyOfRange(idx1, st, end);
        int idx = 0;
        for (int i = maxLen - idx1.length; i < maxLen; i++) {
            ret1[i] = idx1[idx];
            idx++;
        }
        return ret1;
    }

    private static void writeToFile(int maxLen, BufferedWriter x1, BufferedWriter x2, BufferedWriter cpc, BufferedWriter y, int[] indices, int[] indices2, int cpcIdx, int label) throws IOException {
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
            cpc.write(String.valueOf(cpcIdx)+"\n");
        }
    }
}
