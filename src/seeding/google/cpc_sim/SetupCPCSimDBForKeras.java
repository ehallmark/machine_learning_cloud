package seeding.google.cpc_sim;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import graphical_modeling.model.edges.UndirectedEdge;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SetupCPCSimDBForKeras {
    private static final Random rand = new Random(1235018);
    public static void main(String[] args) throws Exception {
        // this file sets up the data table in SQL so that CPCSim.py can run smoothly
        final CPCHierarchy hierarchy = CPCHierarchy.get();
        final boolean reingestIndices = false; // only set to true for the first run through
        final int negativeSamples = 5;
        final long maxDatapoints = 100000000L;
        final int alpha = 500;
        List<String> allCPCs = new ArrayList<>(hierarchy.getLabelToCPCMap().keySet());
        Collections.sort(allCPCs);

        System.out.println("Total num CPCs: "+allCPCs.size()); // remember this for python constant
        System.out.println("Updating index database...");
        Map<String,Integer> codeToIndexMap = new HashMap<>();
        Connection seedConn = Database.newSeedConn();
        PreparedStatement insertIndices = seedConn.prepareStatement("insert into big_query_cpc_occurrence_ids (id,code) values (?,?) on conflict (id) do update set code=excluded.code");
        for(int i = 0; i < allCPCs.size(); i++) {
            codeToIndexMap.put(allCPCs.get(i),i);
            if(reingestIndices) {
                insertIndices.setInt(1, i);
                insertIndices.setString(2, allCPCs.get(i));
                insertIndices.executeUpdate();
            }
        }
        System.out.println("Finished updating index database.");
        if(reingestIndices) {
            seedConn.commit();
        }
        insertIndices.close();

        System.out.println("Adding bayesian starting alphas...");

        // now fill in with actual data
        PreparedStatement seedPs = seedConn.prepareStatement("select publication_number_full,tree from big_query_cpc_tree tablesample bernoulli (10)");
        seedPs.setFetchSize(100);
        ResultSet rs = seedPs.executeQuery();

        System.out.println("Iterating over patent data...");
        AtomicLong cnt = new AtomicLong(0);
        File folder = new File("/home/ehallmark/Downloads/cpc_sim_data/");
        if(!folder.exists()) {
            folder.mkdirs();
        }

        System.out.println("Creating initial distribution...");
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(folder, "data-0.csv")));
        for(CPC node : hierarchy.getLabelToCPCMap().values()) {
            int idx1 = codeToIndexMap.get(node.getName());
            Collection<CPC> ancestors = hierarchy.cpcWithAncestors(node);
            ancestors.remove(node);
            if(ancestors.size()>0) {
                for(int i = 0; i < alpha; i++) {
                List<String> pos = ancestors.stream().map(cpc->cpc.getName()).collect(Collectors.toList());
                    String r = pos.get(rand.nextInt(pos.size()));
                    int idx2 = codeToIndexMap.get(r);
                    writer.write(String.valueOf(idx1)+","+String.valueOf(idx2)+",1\n");
                }
            }
        }
        writer.flush();
        System.out.println("Starting to read data from postgres...");
        AtomicLong maxNum = new AtomicLong(0);
        while(rs.next() && cnt.get()<maxDatapoints) {
            final String[] _tree = (String[])rs.getArray(2).getArray();
            String[] tree = Stream.of(_tree).filter(cpc->codeToIndexMap.containsKey(cpc)).toArray(s->new String[s]);
            for(int i = 0; i < tree.length; i++) {
                String cpc = tree[i];
                for(int j = i+1; j < tree.length; j++) {
                    // randomly skip a quarter of the data
                    if(rand.nextDouble()>0.1) continue;
                    String cpc2 = tree[j];
                    int idx1 = codeToIndexMap.get(cpc);
                    int idx2 = codeToIndexMap.get(cpc2);
                    try {
                        writer.write(String.valueOf(idx1)+","+String.valueOf(idx2)+",1\n");
                        for(int n = 0; n < negativeSamples; n++) {
                            writer.write(String.valueOf(idx1)+","+rand.nextInt(allCPCs.size())+",0\n");
                        }
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    if (cnt.getAndIncrement() % 100000 == 99999) {
                        try {
                            writer.flush();
                        } catch(Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
            if(maxNum.getAndIncrement()%100==99) {
                System.out.println("Finished: "+maxNum.get()+"\t Num sequences: "+cnt.get());
            }
        }

        writer.flush();
        writer.close();
        rs.close();
        seedPs.close();
        seedConn.close();
    }
}
