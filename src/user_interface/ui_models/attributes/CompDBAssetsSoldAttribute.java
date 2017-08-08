package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.*;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 1/27/2017.
 */
public class CompDBAssetsSoldAttribute extends AbstractAttribute<Integer> {
    private static Map<String,Integer> MODEL;

    @Override
    public Integer attributesFor(Collection<String> portfolio, int limit) {
        if(MODEL==null) return 0;
        Integer count = MODEL.get(portfolio.stream().findAny().get());
        if(count==null) return 0;
        return count;
    }

    @Override
    public String getName() {
        return Constants.COMPDB_ASSETS_SOLD;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    public CompDBAssetsSoldAttribute() {
        if(MODEL==null)MODEL=runModel();
    }


    private static Map<String,Integer> runModel(){
        System.out.println("Starting to load CompDBAssetsSoldEvaluator evaluator...");
        Map<String,Integer> assigneeToAssetsSoldCountMap = Database.getCompDBAssigneeToAssetsSoldCountMap();
        return assigneeToAssetsSoldCountMap;
    }

    @Override
    public String getType() {
        return "integer";
    }
}
