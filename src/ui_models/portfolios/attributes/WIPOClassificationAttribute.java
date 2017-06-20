package ui_models.portfolios.attributes;

import classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOClassificationAttribute implements AbstractAttribute<String> {
    public static Map<String,String> definitionMap;
    public static Map<String,String> wipoMap;

    public WIPOClassificationAttribute() {
        if(definitionMap==null) definitionMap= WIPOHelper.getDefinitionMap();
        if(wipoMap==null) wipoMap = WIPOHelper.getWIPOMapWithAssignees();
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String attr = wipoMap.get(portfolio.stream().findAny().get());
        if(attr!=null) {
            String title = definitionMap.get(attr);
            if(title!=null) return title;
        }
        return "";
    }

    @Override
    public String getName() {
        return Constants.WIPO_TECHNOLOGY;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
