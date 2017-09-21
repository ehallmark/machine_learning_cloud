package test;

import com.googlecode.concurrenttrees.radix.RadixTree;
import org.deeplearning4j.berkeley.Pair;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToCitedAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

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
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

        Collection<String> assets = assignees.stream().flatMap(assignee->assigneeToAssetsMap.getPatentDataMap().getOrDefault(assignee, Collections.emptyList()).stream()).collect(Collectors.toSet());

        // citations
        AssetToCitedAssetsMap assetToCitedAssetsMap = new AssetToCitedAssetsMap();

        Collection<Pair<String,Collection<String>>> citedAssets = assets.stream().map(asset->new Pair<>(asset,assetToCitedAssetsMap.getPatentDataMap().getOrDefault(asset,Collections.emptyList()))).collect(Collectors.toList());

        Map<String,Collection<String>> assetToBMCMap = new HashMap<>();
        citedAssets.forEach(p->{
            p.getSecond().forEach(cite->{
                Collection<String> coll;
                if(cite.contains("/")) {
                    coll = filingToAssetMap.getPatentDataMap().getOrDefault(cite,Collections.emptyList());
                } else {
                    coll = Arrays.asList(cite);
                }
                coll.forEach(a->{
                    assetToBMCMap.putIfAbsent(a,new HashSet<>());
                    assetToBMCMap.get(a).add(p.getFirst());
                });
            });
        });

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/bmc_cited_assets.csv")));
        writer.write("Cited Asset,Assignee,BMC Asset(s)\n");
        assetToBMCMap.entrySet().forEach(e->{
            try {
                String assignee = Database.assigneeFor(e.getKey());
                if(assignee==null) assignee = "";
                if(assignee.toUpperCase().contains("BMC")) return;
                if(assignee.contains(",")&&assignee.contains("assignee=")) {
                    assignee = assignee.substring(assignee.indexOf("assignee="),assignee.indexOf(",", assignee.indexOf("assignee=")));
                }
                writer.write(e.getKey()+","+assignee+","+String.join("; ",e.getValue())+"\n");
            } catch(Exception e2) {
                e2.printStackTrace();
            }
        });
        writer.flush();
        writer.close();
    }


}
