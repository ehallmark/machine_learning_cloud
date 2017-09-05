package models.value_models.regression;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

/**
 * Created by ehallmark on 5/9/17.
 */
public class PageRankEvaluator extends ValueAttr {

    @Override
    public String getName() {
        return Constants.PAGE_RANK_VALUE;
    }

    public static void main(String[] args) throws Exception {
        PageRankEvaluator pageRank = new PageRankEvaluator();
        File excelFile = new File("data/page-rank-gather.csv");
        try(BufferedWriter writer = new BufferedWriter(new FileWriter(excelFile))) {
            writer.write("asset,pageRank\n");
            for (String asset : Database.getGatherAssets()) {
                if(asset.contains(",")) return;
                if(asset.startsWith("US")) asset = asset.replace("US","");
                writer.write(asset+","+pageRank.getPatentDataMap().getOrDefault(asset,pageRank.getApplicationDataMap().getOrDefault(asset,0))+"\n");
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
