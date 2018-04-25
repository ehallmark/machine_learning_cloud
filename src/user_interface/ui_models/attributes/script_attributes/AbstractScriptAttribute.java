package user_interface.ui_models.attributes.script_attributes;

import lombok.Setter;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;


/**
 * Created by ehallmark on 8/18/17.
 */
public abstract class AbstractScriptAttribute extends AbstractAttribute  {
    protected static final long millisecondsPerYear = 31557600000L;
    protected static final long millisecondsPerDay = 86400000L;
    protected static final String TREND_PARAM_EXPRESSION = "trend";
    protected static final String TREND_PARAM_PAINLESS = "params.trend";
    @Setter
    private static boolean isBigQuery = false;

    private String getPriorityDate() {
        return isBigQuery ? Attributes.PRIORITY_DATE : Constants.PRIORITY_DATE;
    }

    private String getFilingDate() {
        return isBigQuery ? Attributes.FILING_DATE : Constants.FILING_DATE;
    }

    private String getTermAdjustments() {
        return isBigQuery ? Attributes.TERM_ADJUSTMENTS : Constants.PATENT_TERM_ADJUSTMENT;
    }

    public static double getCurrentYearAndMonth() {
        return new Double(LocalDate.now().getYear()) + (new Double(LocalDate.now().getMonthValue() - 1) / 12d);
    }

    public AbstractScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    protected String getPriorityDateField(String dateField) {
        return "(doc['"+ getPriorityDate()+"'].empty ? doc['"+getFilingDate()+"']."+dateField+" : doc['"+getPriorityDate()+"']."+dateField+")";
    }

    protected String getCalculatedPriorityDateField() {
        return getPriorityDateField("value")+"+("+getTermExtensionField()+"*"+millisecondsPerDay+")";
    }

    protected String getTermExtensionField() {
        return "(doc['"+getTermAdjustments()+"'].empty ? 0 : doc['"+getTermAdjustments()+"'].value)";
    }

    public abstract Map<String,Object> getParams();

    public abstract Script getScript(boolean requireParams, boolean idOnly);

    // override
    public Script getSortScript() {
        return getScript(true, false);
    }

    public String getRemainingLifeQuery(boolean expression) {
        return "("+getPriorityDateField("date.year")+"+20+("+getPriorityDateField("date.monthOfYear")+"-1)/12)+("+getTermExtensionField()+"/365.25)- "+(expression?TREND_PARAM_EXPRESSION:TREND_PARAM_PAINLESS);
    }


    public static QueryBuilder getSortQuery(Script searchScript, FiltersFunctionScoreQuery.ScoreMode scoreMode, float weight) {
        if(searchScript==null) return null;
        //System.out.println("Getting "+getName()+" sort script");
        return  QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(searchScript).setWeight(weight))
                .boostMode(CombineFunction.SUM)
                .scoreMode(scoreMode);
    }

    public int getWeight() {
        return 1;
    }
}
