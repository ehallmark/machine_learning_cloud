package seeding.google.elasticsearch;

import elasticsearch.DataSearcher;
import org.elasticsearch.search.sort.SortOrder;
import seeding.google.attributes.AssigneeHarmonized;
import seeding.google.attributes.Constants;
import seeding.google.mongo.IngestPatents;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AdvancedKeywordFilter;
import user_interface.ui_models.portfolios.items.ItemTransformer;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class SearchForAssignees {
    public static void main(String[] args) throws Exception {
        String index = IngestPatents.INDEX_NAME;
        String type = IngestPatents.TYPE_NAME;

        Collection<AbstractAttribute> attributes = Constants.buildAttributes();

        Map<String,NestedAttribute> nestedAttributeMap = attributes.stream()
                .filter(attr->attr instanceof NestedAttribute)
                .collect(Collectors.toMap(e->e.getFullName(),e->(NestedAttribute)e));

        AdvancedKeywordFilter assigneeFilter = new AdvancedKeywordFilter(new AssigneeHarmonized(), AbstractFilter.FilterType.AdvancedKeyword);
        assigneeFilter.setQueryStr("amazon | google");


        ItemTransformer transformer = item -> {
            // TODO implement item transformer body

            return null;
        };

        DataSearcher.searchForAssets(index,type,attributes, Collections.singleton(assigneeFilter), seeding.Constants.NO_SORT, SortOrder.ASC, 1000000,nestedAttributeMap,transformer,false,false,true);


    }
}
