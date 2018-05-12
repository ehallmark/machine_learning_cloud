package seeding.google.cpc_sim;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import graphical_modeling.model.edges.UndirectedEdge;
import org.nd4j.linalg.api.ndarray.INDArray;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class SetupCPCSimDBForKeras {
    private static final Random rand = new Random(1235018);
    public static void main(String[] args) throws Exception {
        // this file sets up the data table in SQL so that CPCSim.py can run smoothly
        final CPCHierarchy hierarchy = CPCHierarchy.get();
        final double alpha = 200d;
        final boolean reingestIndices = false; // only set to true for the first run through

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
        seedConn.commit();
        insertIndices.close();

        System.out.println("Adding bayesian starting alphas...");

        // bayesian initial (similarity to hierarchy)
        Map<String,Double> occurrenceMap = new HashMap<>();
        final Map<UndirectedEdge<String>,AtomicDouble> cooccurrenceMap = new ConcurrentHashMap<>(allCPCs.size()+1);
        List<CPC> cpcs = new ArrayList<>(hierarchy.getLabelToCPCMap().values());
        Map<String,Set<String>> cpcToOccurrenceMap = new ConcurrentHashMap<>(cpcs.size()+1);
        cpcs.forEach(cpc->{
            occurrenceMap.put(cpc.getName(),alpha);
            cpcToOccurrenceMap.putIfAbsent(cpc.getName(), new HashSet<>());
            hierarchy.cpcWithAncestors(cpc).forEach(cpc2->{
                UndirectedEdge<String> edge = new UndirectedEdge<>(cpc.getName(),cpc2.getName());
                if(!cooccurrenceMap.containsKey(edge)) {
                    cooccurrenceMap.put(edge, new AtomicDouble(alpha));
                }
                cpcToOccurrenceMap.get(cpc.getName()).add(cpc2.getName());

            });
            if(cpc.getParent()!=null) {
                cpc.getParent().getChildren().forEach(child -> {
                    UndirectedEdge<String> edge = new UndirectedEdge<>(cpc.getName(),child.getName());
                    if(!cooccurrenceMap.containsKey(edge)) {
                        cooccurrenceMap.put(edge, new AtomicDouble(alpha));
                    }
                    cpcToOccurrenceMap.get(cpc.getName()).add(cpc.getName());
                    child.getChildren().forEach(grandChild->{
                        UndirectedEdge<String> edge2 = new UndirectedEdge<>(cpc.getName(),grandChild.getName());
                        if(!cooccurrenceMap.containsKey(edge2)) {
                            cooccurrenceMap.put(edge2, new AtomicDouble(alpha));
                        }
                        cpcToOccurrenceMap.get(cpc.getName()).add(grandChild.getName());
                    });
                });
            }
        });

        System.out.println("Cooccurrence Size After Initialization: "+cooccurrenceMap.size());

        // now fill in with actual data
        PreparedStatement seedPs = seedConn.prepareStatement("select publication_number_full,tree from big_query_cpc_tree tablesample bernoulli (5)");
        seedPs.setFetchSize(10);
        ResultSet rs = seedPs.executeQuery();


        System.out.println("Iterating over patent data...");
        AtomicInteger cnt = new AtomicInteger(0);
        ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        while(rs.next()) {
            final String[] _tree = (String[])rs.getArray(2).getArray();
            service.execute(() -> {
                String[] tree = Stream.of(_tree).filter(cpc->{
                    boolean valid = occurrenceMap.containsKey(cpc);
                    if(valid) {
                        // add to occurrence map
                        synchronized (occurrenceMap) {
                            occurrenceMap.put(cpc, occurrenceMap.get(cpc) + 1d);
                        }
                    }
                    return valid;
                }).toArray(s->new String[s]);
                for(int i = 0; i < tree.length; i++) {
                    String cpc = tree[i];
                    for(int j = i+1; j < tree.length; j++) {
                        String cpc2 = tree[j];
                        UndirectedEdge<String> edge = new UndirectedEdge<>(cpc,cpc2);
                        synchronized (cooccurrenceMap) {
                            if (!cooccurrenceMap.containsKey(edge)) {
                                cooccurrenceMap.put(edge, new AtomicDouble(alpha));
                            }
                            cooccurrenceMap.get(edge).getAndAdd(1d);
                        }
                        cpcToOccurrenceMap.get(cpc).add(cpc2);
                    }
                }
                if(cnt.getAndIncrement()%10000==9999) {
                    System.out.println("Finished: "+cnt.get()+" \tNum Occurences: "+cooccurrenceMap.size());
                }

            });
        }
        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch(Exception e) {
            e.printStackTrace();
        }
        rs.close();
        seedPs.close();
        System.out.println("Cooccurrence Size After Training: "+cooccurrenceMap.size());

        System.out.println("Truncating previous results...");
        seedConn.createStatement().executeUpdate("truncate big_query_cpc_occurrence");
        seedConn.commit();
        System.out.println("Truncated succesfully.");

        System.out.println("Saving cooccurrence results to database...");

        cnt.set(0);
        IntStream.range(0,allCPCs.size()).forEach(idx->{
            String cpc = allCPCs.get(idx);
            double sum = 0d;
            Map<Integer,Double> occurrences = new HashMap<>();
            List<Integer> negatives = new ArrayList<>();
            Set<String> occurring = cpcToOccurrenceMap.get(cpc);
            if(occurring==null||occurring.isEmpty()) {
                System.out.println("Warning: No occurring cpcs for: "+cpc);
                return;
            }
            for(String occ : occurring) {
                if(occ.equals(cpc)) continue;
                int cIdx = codeToIndexMap.get(occ);
                double val = cooccurrenceMap.getOrDefault(new UndirectedEdge<>(cpc,occ),new AtomicDouble(0d)).get() * Math.pow(cpcs.get(cIdx).getNumParts(),2);
                occurrences.put(cIdx,val);
                sum+=val;
            }

            // random subset
            final double _sum = sum;
            int s = Math.round((float)Math.exp(Math.max(6-hierarchy.getLabelToCPCMap().get(cpc).getNumParts(),1)));
            final int negativeSamples = (int)s;

            for(int i = 0; i < negativeSamples * 10 && negatives.size() < negativeSamples; i++) {
                int r = rand.nextInt(allCPCs.size());
                if(r!=idx) {
                    if(!occurrences.containsKey(r)) {
                        negatives.add(i);
                    }
                }
            }


            Set<String> samples = new HashSet<>();
            double[] randoms = IntStream.range(0, s).mapToDouble(i->rand.nextDouble()*_sum).toArray();
            double[] holders = new double[randoms.length];

            for(int j = 0; j < allCPCs.size(); j++) {
                if(j==idx) continue;
                double v = occurrences.getOrDefault(j,0d);
                for(int r = 0; r < randoms.length; r++) {
                    holders[r]+=v;
                    if(holders[r]>randoms[r]) {
                        samples.add(allCPCs.get(j));
                        randoms[r]=Double.MAX_VALUE; // stops this index
                    }
                }
            }

            if(samples.isEmpty()) {
                System.out.println("Warning: sample "+cpc+" is empty.");
                return;
            }

            StringJoiner values = new StringJoiner(",");
            for(String sample : samples) {
                StringJoiner value = new StringJoiner(",","(",")");
                value.add(codeToIndexMap.get(cpc).toString()).add(codeToIndexMap.get(sample).toString()).add("1.0");
                values.add(value.toString());
            }

            if(negatives.size() < s) {
                System.out.println("Skipping negatives for "+cpc);
            } else {
                for (int i = 0; i < Math.min(negatives.size(), negativeSamples); i++) {
                    int neg = rand.nextInt(negatives.size());
                    StringJoiner value = new StringJoiner(",", "(", ")");
                    value.add(codeToIndexMap.get(cpc).toString()).add(codeToIndexMap.get(allCPCs.get(neg)).toString()).add("0.0");
                    values.add(value.toString());
                }
            }


            try {
                String insert = "insert into big_query_cpc_occurrence (id1,id2,freq) values "+values.toString()+" on conflict (id1,id2) do nothing";
                seedConn.createStatement().executeUpdate(insert);
            } catch(Exception e) {
                e.printStackTrace();
            }
            if (cnt.getAndIncrement() % 10000 == 9999) {
                System.out.println("Completed: " + cnt.get());
                try {
                    seedConn.commit();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }

        });

        seedConn.commit();
        seedConn.close();
    }
}
