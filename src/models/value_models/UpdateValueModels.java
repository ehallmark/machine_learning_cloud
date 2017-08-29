package models.value_models;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.CompDBAssetsPurchasedAttribute;
import user_interface.ui_models.attributes.computable_attributes.CompDBAssetsSoldAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToMaintenanceFeeReminderCountMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateValueModels {
    public static void main(String[] args) throws Exception{
        File dataFile = new File(Constants.DATA_FOLDER+"value-models-testing.csv");
        List<ComputableAttribute<? extends Number>> attributes = Arrays.asList(new AssetToMaintenanceFeeReminderCountMap(), new PageRankEvaluator(), new CompDBAssetsSoldAttribute(), new CompDBAssetsPurchasedAttribute());

        Map<String,Boolean> gatherValueMap = Database.getGatherValueMap();
        Collection<String> gatherPatents = new ArrayList<>(gatherValueMap.keySet());

        try(BufferedWriter writer = new BufferedWriter(new FileWriter(dataFile))) {
            for(String asset : gatherPatents) {
                writer.write(asset+","+String.join(",",attributes.stream().map(attr-> {
                    Number value = attr.getPatentDataMap().get(asset);
                    if(value==null) value = 0;
                    return value.toString();
                }).collect(Collectors.toList()))+"\n");
            }
            writer.flush();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }


        OverallEvaluator.main(args);
    }
}
