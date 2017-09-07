package models.value_models;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.OverallEvaluator;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.util.*;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateValueModels {
    public static void main(String[] args) throws Exception{
        // train wipo
        SimilarPatentServer.initialize(true,false);
        WIPOValueModel.main(args);
        List<ValueAttr> models = Arrays.asList(
                new OverallEvaluator(),
                new WIPOValueModel()
        );
        List<Double> weights = Arrays.asList(
                75d,
                25d
        );
        ValueAttr aiValueModel = new ValueModelCombination(Constants.AI_VALUE,models,weights);
        ItemTransformer transformer = new ItemTransformer() {
            @Override
            public Item transform(Item item) {
                Object parent = item.getData("_parent");
                if(parent==null) return item;
                Map<String,Object> updates = new HashMap<>();
                updates.put(aiValueModel.getFullName(),aiValueModel.evaluate(item));
                DataIngester.ingestBulk(item.getName(),parent.toString(),updates,false);
                return item;
            }
        };
        DataSearcher.searchForAssets(SimilarPatentServer.getAllTopLevelAttributes(),Collections.emptyList(),null, SortOrder.ASC, 8000000,SimilarPatentServer.getNestedAttrMap(), transformer, false);
        DataIngester.close();
    }
}
