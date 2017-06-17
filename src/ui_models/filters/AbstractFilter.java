package ui_models.filters;

import j2html.tags.Tag;
import lombok.Getter;
import lombok.NonNull;
import seeding.Database;
import spark.QueryParamsMap;
import spark.Request;
import ui_models.attributes.AbstractAttribute;
import ui_models.portfolios.items.Item;

import java.util.Collection;

/**
 * Created by Evan on 5/9/2017.
 */
public abstract class AbstractFilter {
    enum Type {
        PreFilter, PostFilter
    }
    public abstract void extractRelevantInformationFromParams(Request params);

    public abstract Tag getOptionsTag();

    public boolean defaultSelected() {
        return false;
    }

    public abstract boolean shouldKeepItem(Item obj);
}
