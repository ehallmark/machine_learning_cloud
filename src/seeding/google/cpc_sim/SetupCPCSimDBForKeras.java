package seeding.google.cpc_sim;

import com.google.common.util.concurrent.AtomicDouble;
import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import graphical_modeling.model.edges.UndirectedEdge;
import seeding.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class SetupCPCSimDBForKeras {
    public static void main(String[] args) throws Exception {
        // this file sets up the data table in SQL so that CPCSim.py can run smoothly
        final CPCHierarchy hierarchy = CPCHierarchy.get();
        final double alpha = 20d;

        List<String> allCPCs = new ArrayList<>(hierarchy.getLabelToCPCMap().keySet());
        Collections.sort(allCPCs);

        System.out.println("Total num CPCs: "+allCPCs.size()); // remember this for python constant
        System.out.println("Updating index database...");
        Map<String,Integer> codeToIndexMap = new HashMap<>();
        Connection seedConn = Database.newSeedConn();
        PreparedStatement insertIndices = seedConn.prepareStatement("insert into big_query_cpc_occurrence_ids (id,code) values (?,?) on conflict (id) do update set code=excluded.code");
        for(int i = 0; i < allCPCs.size(); i++) {
            codeToIndexMap.put(allCPCs.get(i),i);
            insertIndices.setInt(1,i);
            insertIndices.setString(2,allCPCs.get(i));
            insertIndices.executeUpdate();
        }
        System.out.println("Finished updating index database.");
        seedConn.commit();
        insertIndices.close();

        System.out.println("Adding bayesian starting alphas...");

        // bayesian initial (similarity to hierarchy)
        Map<String,Integer> occurrenceMap = new HashMap<>();
        Map<UndirectedEdge<String>,AtomicDouble> cooccurrenceMap = new HashMap<>();
        List<CPC> cpcs = new ArrayList<>(hierarchy.getLabelToCPCMap().values());
        cpcs.forEach(cpc->{
            occurrenceMap.put(cpc.getName(),(int)alpha);
            hierarchy.cpcWithAncestors(cpc).forEach(cpc2->{
                if(!cooccurrenceMap.containsKey(new UndirectedEdge<>(cpc.getName(),cpc2.getName()))) {
                    cooccurrenceMap.put(new UndirectedEdge<>(cpc.getName(),cpc2.getName()), new AtomicDouble(alpha));
                }
            });
        });

        System.out.println("Cooccurrence Size After Initialization: "+cooccurrenceMap.size());

        // now fill in with actual data
        PreparedStatement seedPs = seedConn.prepareStatement("select publication_number_full,tree from big_query_cpc_tree tablesample system (25)");
        seedPs.setFetchSize(10);
        ResultSet rs = seedPs.executeQuery();


        System.out.println("Iterating over patent data...");
        AtomicInteger cnt = new AtomicInteger(0);
        while(rs.next()) {
            String[] tree = (String[])rs.getArray(2).getArray();
            tree = Stream.of(tree).filter(cpc->occurrenceMap.containsKey(cpc)).toArray(s->new String[s]);
            for(int i = 0; i < tree.length; i++) {
                for(int j = i+1; j < tree.length; j++) {
                    String cpc = tree[i];
                    String cpc2 = tree[j];
                    UndirectedEdge<String> edge = new UndirectedEdge<>(cpc,cpc2);
                    if(!cooccurrenceMap.containsKey(edge)) {
                        cooccurrenceMap.put(edge, new AtomicDouble(alpha));
                    }
                    cooccurrenceMap.get(edge).getAndAdd(1d);
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished: "+cnt.get()+" \t\tNum Occurences: "+cooccurrenceMap.size());
            }
        }
        rs.close();
        seedPs.close();
        System.out.println("Cooccurrence Size After Training: "+cooccurrenceMap.size());

        System.out.println("Saving cooccurrence results to database...");

        cnt.set(0);
        PreparedStatement insert = seedConn.prepareStatement("insert into big_query_cpc_occurrence (id1,id2,freq) values (?,?,?) on conflict (id1,id2) do update set freq=excluded.freq");
        cooccurrenceMap.forEach((edge,d)-> {
            double v = d.get()/Math.sqrt(occurrenceMap.get(edge.getNode1())*occurrenceMap.get(edge.getNode2()));
            try {
                insert.setInt(1, codeToIndexMap.get(edge.getNode1()));
                insert.setInt(2, codeToIndexMap.get(edge.getNode2()));
                insert.setDouble(3, v);
                insert.executeUpdate();
                if (cnt.getAndIncrement() % 10000 == 9999) {
                    System.out.println("Completed: " + cnt.get());
                    seedConn.commit();
                }
            } catch(Exception e) {
                e.printStackTrace();
            }
        });

        seedConn.commit();
        seedConn.close();
    }
}
