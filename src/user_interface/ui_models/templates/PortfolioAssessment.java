package user_interface.ui_models.templates;

import seeding.Constants;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
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
        map.put(Constants.PIE_CHART,Arrays.asList(Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(SimilarPatentServer.ATTRIBUTES_ARRAY_FIELD,Arrays.asList(Constants.AI_VALUE,Constants.NAME,Constants.LATEST_ASSIGNEE,Constants.PORTFOLIO_SIZE,Constants.WIPO_TECHNOLOGY,Constants.TECHNOLOGY));
        map.put(Constants.NAME + Constants.FILTER_SUFFIX,"");
        map.put(Constants.LATEST_ASSIGNEE + Constants.FILTER_SUFFIX,"");
        map.put(Constants.DOC_TYPE + Constants.FILTER_SUFFIX, Arrays.asList(PortfolioList.Type.patents));
        return map;
    }

}
