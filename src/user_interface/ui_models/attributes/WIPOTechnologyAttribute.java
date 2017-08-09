package user_interface.ui_models.attributes;

import models.classification_models.WIPOHelper;
import j2html.tags.Tag;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/18/2017.
 */
public class WIPOTechnologyAttribute extends ComputableAttribute<String> {
    public static Map<String,String> definitionMap;
    public static Map<String,String> wipoMap;

    public WIPOTechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
        if(definitionMap==null) definitionMap= WIPOHelper.getDefinitionMap();
        if(wipoMap==null) wipoMap = WIPOHelper.getWIPOMapWithAssignees();
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        String attr = wipoMap.get(item);
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

    @Override
    public String getType() {
        return "keyword";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Multiselect;
    }
}
