package models.value_models;

import elasticsearch.DataIngester;
import elasticsearch.DataSearcher;
import models.value_models.graphical.UpdateGraphicalModels;
import models.value_models.graphical.WIPOValueModel;
import models.value_models.regression.AIValueModel;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.computable_attributes.OverallEvaluator;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.ResultTypeAttribute;
import user_interface.ui_models.attributes.computable_attributes.ValueAttr;
import user_interface.ui_models.attributes.WIPOTechnologyAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.PortfolioList;
import user_interface.ui_models.portfolios.items.Item;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by ehallmark on 7/12/17.
 */
public class UpdateValueModels {
    public static void main(String[] args) throws Exception{
        updateLatest(null);
    }

    public static void updateLatest(Collection<String> onlyUpdateAssets) throws Exception {
        final boolean debug = false;

        // train wipo
        SimilarPatentServer.initialize(true,false);
        WIPOValueModel.main(null);

        ValueAttr aiValueModel = new OverallEvaluator(true);
        aiValueModel.initMaps();

        AtomicLong patentCnt = new AtomicLong(0);
        AtomicLong appCnt = new AtomicLong(0);
        Map<String,Number> patentModel = aiValueModel.getPatentDataMap();
        Map<String,Number> applicationModel = aiValueModel.getApplicationDataMap();
        ItemTransformer transformer = new ItemTransformer() {
            @Override
            public Item transform(Item item) {
                boolean isPatent = item.getDataMap().getOrDefault(Constants.DOC_TYPE, "patents").toString().equals(PortfolioList.Type.patents.toString());

                // don't compute if already seen
                if(isPatent) {
                    if(patentCnt.getAndIncrement()%10000==0) {
                        System.out.println("Seen patents: "+patentCnt.get());
                    }
                    if(patentModel.containsKey(item.getName())) {
                        return null;
                    }
                } else {
                    if(appCnt.getAndIncrement()%10000==0) {
                        System.out.println("Seen applications: "+appCnt.get());
                    }
                    if(applicationModel.containsKey(item.getName())) {
                        return null;
                    }
                }

                double aiValue = aiValueModel.evaluate(item);
                if(debug) System.out.println("Value: "+aiValue);
                if(isPatent) {
                    patentModel.put(item.getName(),aiValue);
                } else {
                    applicationModel.put(item.getName(),aiValue);
                }
                return null;
            }
        };
        Collection<AbstractAttribute> toSearchFor = new ArrayList<>();
        toSearchFor.addAll(AIValueModel.MODELS);
        toSearchFor.add(new WIPOTechnologyAttribute());
        toSearchFor.add(new ResultTypeAttribute());
        List<AbstractFilter> filters;
        if(onlyUpdateAssets == null) {
            filters = Collections.emptyList();
        } else {
            filters = Arrays.asList(new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text, onlyUpdateAssets));
        }
        DataSearcher.searchForAssets(toSearchFor,filters,null, SortOrder.ASC, 20000000,SimilarPatentServer.getNestedAttrMap(), transformer, false,false);
        aiValueModel.save();
    }
}
