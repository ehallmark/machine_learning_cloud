package models.value_models;

import elasticsearch.DataSearcher;
import models.value_models.regression.AIValueModel;
import models.value_models.regression.RegressionValueModel;
import org.elasticsearch.search.sort.SortOrder;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.computable_attributes.GatherBoolValueAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractIncludeFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;

public class PullAttributesForRegression {
    private static final File regressionDataFile = new File(Constants.DATA_FOLDER+"regression_data_file_excel.csv");
    public static void main(String[] args) {
        Set<String> gatherAssets = Database.getGatherAssets();
        System.out.println("Num gather assets: "+gatherAssets.size());

        Collection<AbstractAttribute> attributes = new ArrayList<>(AIValueModel.MODELS);
        attributes.add(new GatherBoolValueAttribute());

        List<Item> items = DataSearcher.searchForAssets(attributes,Collections.singletonList(new AbstractIncludeFilter(new AssetNumberAttribute(), AbstractFilter.FilterType.Include, AbstractFilter.FieldType.Text,new ArrayList<>(gatherAssets))), Constants.NO_SORT, SortOrder.ASC, gatherAssets.size(), Collections.emptyMap(),false,false);

        StringJoiner header = new StringJoiner("\",\"","\"","\"\n");
        attributes.forEach(attr->header.add(attr.getName()));
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(regressionDataFile))) {
            writer.write(header.toString());
            for(Item item : items) {
                StringJoiner line = new StringJoiner(",", "", "\n");
                attributes.forEach(attr -> {
                    Number num = RegressionValueModel.valueFromAttribute(item, attr, 0);
                    line.add(num.toString());
                });
                writer.write(line.toString());
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
