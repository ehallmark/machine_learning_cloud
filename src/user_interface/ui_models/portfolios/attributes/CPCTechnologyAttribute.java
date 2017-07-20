package user_interface.ui_models.portfolios.attributes;

import j2html.tags.Tag;
import models.classification_models.WIPOHelper;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by Evan on 6/18/2017.
 */
public class CPCTechnologyAttribute implements AbstractAttribute<String> {
    public static Map<String,String> definitionMap;

    public CPCTechnologyAttribute() {
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
    public Tag getOptionsTag() {
        return div();
    }
}
