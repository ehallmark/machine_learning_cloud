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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateValueModels {
    public static void main(String[] args) throws Exception{
        final boolean debug = false;
        // train wipo
        SimilarPatentServer.initialize(true,false);
        WIPOValueModel.main(args);
        List<ValueAttr> models = Arrays.asList(
                new OverallEvaluator(),
                new WIPOValueModel()
        );
        List<Double> weights = Arrays.asList(
                70d,
                30d
        );
        ValueAttr aiValueModel = new ValueModelCombination(Constants.AI_VALUE,models,weights);
        AtomicLong cnt = new AtomicLong(0);
        ItemTransformer transformer = new ItemTransformer() {
            @Override
            public Item transform(Item item) {
                Object parent = item.getData("_parent");
                if(parent==null) return item;
                Map<String,Object> updates = new HashMap<>();
                double aiValue = aiValueModel.evaluate(item);
                if(debug) System.out.println("Value: "+aiValue);
                updates.put(aiValueModel.getFullName(),aiValue);
                DataIngester.ingestBulk(item.getName(),parent.toString(),updates,false);
                if(cnt.getAndIncrement()%10000==0) {
                    System.out.println("Seen: "+cnt.get());
                }
                return item;
            }
        };
        DataSearcher.searchForAssets(SimilarPatentServer.getAllTopLevelAttributes(),Collections.emptyList(),null, SortOrder.ASC, 8000000,SimilarPatentServer.getNestedAttrMap(), transformer, false);
        DataIngester.close();
    }
}
