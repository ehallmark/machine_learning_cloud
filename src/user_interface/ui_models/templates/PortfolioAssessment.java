package user_interface.ui_models.templates;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.NestedAttribute;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.filters.AbstractNestedFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 6/24/2017.
 */
public class PortfolioAssessment extends FormTemplate {

    public PortfolioAssessment() {
        super(Constants.PORTFOLIO_ASSESSMENT, getParams(), FormTemplate.defaultOptions());
    }

    private static Map<String,Object> getParams() {
        Map<String,Object> map = new HashMap<>();

        map.put(Constants.HISTOGRAM,Arrays.asList(Constants.AI_VALUE, Constants.REMAINING_LIFE));
        map.put(Constants.LINE_CHART,Arrays.asList(Constants.PRIORITY_DATE));
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.AI_VALUE,Constants.NAME,Constants.LATEST_ASSIGNEE+"."+Constants.ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(simpleNameToFilterName(AbstractFilter.FilterType.Include,Constants.NAME),"");
        map.put(simpleNameToFilterName(AbstractFilter.FilterType.AdvancedKeyword,Constants.LATEST_ASSIGNEE,Constants.ASSIGNEE),"");
        map.put(simpleNameToFilterName(AbstractFilter.FilterType.Include,Constants.DOC_TYPE),PortfolioList.Type.patents.toString());
        return map;
    }

    public static String simpleNameToFilterName(AbstractFilter.FilterType targetFilterType, String... attrStrs) {
        AbstractAttribute attribute = getChildHelper(attrStrs);
        return attribute.createFilters().stream().filter(filter->filter.getFilterType().equals(targetFilterType)).map(filter->filter.getName()).findAny().orElse("");
    }

    private static AbstractAttribute getChildHelper(String[] attrStrs) {
        AbstractAttribute attr = SimilarPatentServer.attributesMap.get(attrStrs[0]);
        for(int i = 1; i < attrStrs.length; i++) {
            String nestedName = attrStrs[i];
            if(attr instanceof NestedAttribute) {
                for(AbstractAttribute nested : ((NestedAttribute)attr).getAttributes()) {
                    if(nested.getName().equals(nestedName)) {
                        attr = nested;
                        break;
                    }
                }
            }
        }
    }

}
