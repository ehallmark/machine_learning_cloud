package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import models.classification_models.WIPOHelper;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/18/2017.
 */
public class CPCTechnologyAttribute extends ComputableAttribute<String> {
    public static Map<String,String> definitionMap;

    public CPCTechnologyAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Include, AbstractFilter.FilterType.Exclude));
        if(definitionMap==null) definitionMap= Database.getClassCodeToClassTitleMap();
    }

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        String attr = Database.classificationsFor(item).stream().map(cpc->cpc.substring(0,3)).collect(Collectors.groupingBy(e->e,Collectors.counting())).entrySet()
                .stream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e->e.getKey()).findFirst().orElse(null);
        if(attr!=null) {
            String title = definitionMap.get(attr);
            if(title!=null) return title;
        }
        return "";
    }

    @Override
    public String getName() {
        return Constants.CPC_TECHNOLOGY;
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
