package test;

import com.googlecode.concurrenttrees.radix.RadixTree;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCitedAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/21/17.
 */
public class BMCTest {
    public static void main(String[] args) throws Exception {
        AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
        Collection<String> assignees = Database.possibleNamesForAssignee("BMC SOFTWARE");
        System.out.println("Num assignees found: "+assignees.size());

        Collection<String> assets = assignees.stream().flatMap(assignee->assigneeToAssetsMap.getPatentDataMap().getOrDefault(assignee, Collections.emptyList()).stream()).collect(Collectors.toSet());

        // citations
        AssetToCitedAssetsMap assetToCitedAssetsMap = new AssetToCitedAssetsMap();

        Collection<Pair<String,Collection<String>>> citedAssets = assets.stream().map(asset->new Pair<>(asset,assetToCitedAssetsMap.getPatentDataMap().getOrDefault(asset,Collections.emptyList()))).collect(Collectors.toList());

        Map<String,Collection<String>> assetToBMCMap = new HashMap<>();
        citedAssets.forEach(p->{
            p.getSecond().forEach(cite->{
                assetToBMCMap.putIfAbsent(cite,new HashSet<>());
                assetToBMCMap.get(cite).add(p.getFirst());
            });
        });

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/bmc_cited_assets.csv")));
        writer.write("Cited Asset,Assignee,BMC Asset\n");
        assetToBMCMap.entrySet().forEach(e->{
            try {
                writer.write(e.getKey()+","+Database.assigneeFor(e.getKey())+","+String.join("; ",e.getValue())+"\n");
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        });
        writer.flush();
        writer.close();
    }


}
