package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import models.value_models.*;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.items.Item;

import java.util.function.Function;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class ValueAttr extends ComputableAttribute<Number> {
    // Instance class
    protected double defaultVal = 0;
    public ValueAttr() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    // Returns value between 1 and 5
    @Override
    public Double attributesFor(Collection<String> portfolio, int n) {
        return portfolio.stream()
                .collect(Collectors.averagingDouble(token->evaluate(new Item(token))));
    }

    public double evaluate(Item item) {
        return this.evaluate(item, Database.isApplication(item.getName()));
    }

    public double evaluate(Item item, boolean isApplication) {
        if(applicationDataMap==null||patentDataMap==null) {
            synchronized (this) {
                this.initMaps();
            }
        }
        String token = item.getName();
        Map<String,Number> model;
        Map<String,Number> backupModel;
        if(isApplication) {
            model = applicationDataMap;
            backupModel = patentDataMap;
        } else {
            model = patentDataMap;
            backupModel = applicationDataMap;
        }
        Number val = model.getOrDefault(token, backupModel.get(token));
        if(val!=null) {
            return val.doubleValue();
        } else {
            return defaultVal;
        }
    }

    @Override
    public Tag getOptionsTag(Function<String,Boolean> userRoleFunction) {
        return div();
    }

    @Override
    public String getType() {
        return "double";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Double;
    }
}
