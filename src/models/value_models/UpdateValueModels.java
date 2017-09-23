package models.value_models;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.value_models.graphical.UpdateGraphicalModels;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.AIValueModel;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.ResultTypeAttribute;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateValueModels {
    public static void main(String[] args) throws Exception{
        final boolean debug = false;
        boolean rerunPageRank = false;
        if(rerunPageRank)UpdateGraphicalModels.main(args); // page rank

        // train wipo
        SimilarPatentServer.initialize(true,false);
        WIPOValueModel.main(args);

        ValueAttr aiValueModel = new OverallEvaluator(true);
        aiValueModel.initMaps();

        AtomicLong patentCnt = new AtomicLong(0);
        AtomicLong appCnt = new AtomicLong(0);
        Map<String,Number> patentModel = aiValueModel.getPatentDataMap();
        Map<String,Number> applicationModel = aiValueModel.getApplicationDataMap();
        ItemTransformer transformer = new ItemTransformer() {
            @Override
            public Item transform(Item item) {

                double aiValue = aiValueModel.evaluate(item);
                if(debug) System.out.println("Value: "+aiValue);
                boolean isPatent = item.getDataMap().getOrDefault(Constants.DOC_TYPE, "patents").toString().equals(PortfolioList.Type.patents.toString());
                if(isPatent) {
                    patentModel.put(item.getName(),aiValue);
                    if(patentCnt.getAndIncrement()%10000==0) {
                        System.out.println("Seen patents: "+patentCnt.get());
                        System.out.println("Sample Patent "+item.getName()+": "+aiValue);
                    }
                } else {
                    applicationModel.put(item.getName(),aiValue);
                    if(appCnt.getAndIncrement()%10000==0) {
                        System.out.println("Seen applications: "+appCnt.get());
                        System.out.println("Sample App "+item.getName()+": "+aiValue);
                    }
                }
                return item;
            }
        };
        Collection<AbstractAttribute> toSearchFor = new ArrayList<>();
        toSearchFor.addAll(AIValueModel.MODELS);
        toSearchFor.add(new WIPOTechnologyAttribute());
        toSearchFor.add(new ResultTypeAttribute());
        DataSearcher.searchForAssets(toSearchFor,Collections.emptyList(),null, SortOrder.ASC, 20000000,SimilarPatentServer.getNestedAttrMap(), transformer, false,false);
        DataIngester.close();
        aiValueModel.save();
    }
}
