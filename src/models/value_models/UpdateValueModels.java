package models.value_models;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.value_models.bayesian.WIPOValueModel;
import models.value_models.regression.OverallEvaluator;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.attributes.hidden_attributes.AssetToMaintenanceFeeReminderCountMap;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

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
        // train wipo model
        WIPOValueModel wipoValueModel = new WIPOValueModel();
        wipoValueModel.init();

        Collection<ValueAttr> regressionModels = Arrays.asList(
                new OverallEvaluator(),
                wipoValueModel
        );

        ItemTransformer transformer = new ItemTransformer() {
            @Override
            public Item transform(Item item) {
                Object parent = item.getData("_parent");
                if(parent==null) return item;
                Map<String,Object> updates = new HashMap<>();
                regressionModels.forEach(evaluator->{
                    updates.put(evaluator.getFullName(),evaluator.evaluate(item));
                });
                DataIngester.ingestBulk(item.getName(),parent.toString(),updates,false);
                return item;
            }
        };
        DataSearcher.searchForAssets(SimilarPatentServer.getAllTopLevelAttributes(),Collections.emptyList(),null, SortOrder.ASC, 8000000,SimilarPatentServer.getNestedAttrMap(), transformer, false);
        DataIngester.close();
    }
}
