package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FiltersFunctionScoreQuery;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;
import org.elasticsearch.script.Script;
import seeding.Constants;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.DependentAttribute;
import user_interface.ui_models.filters.*;

import java.time.LocalDate;
import java.util.Collection;


/**
 * Created by ehallmark on 8/18/17.
 */
public abstract class AbstractScriptAttribute extends AbstractAttribute  {
    protected static final long millisecondsPerYear = 31557600000L;
    protected static final long millisecondsPerDay = 86400000L;

    public static double getCurrentYearAndMonth() {
        return new Double(LocalDate.now().getYear()) + (new Double(LocalDate.now().getMonthValue() - 1) / 12d);
    }

    public AbstractScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    protected String getPriorityDateField(String dateField) {
        return "(doc['"+ Constants.PRIORITY_DATE+"'].empty ? doc['"+Constants.FILING_DATE+"']."+dateField+" : doc['"+Constants.PRIORITY_DATE+"']."+dateField+")";
    }

    protected String getCalculatedPriorityDateField() {
        return getPriorityDateField("value")+"+("+getTermExtensionField()+"*"+millisecondsPerDay+")";
    }

    protected String getTermExtensionField() {
        return "(doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].empty ? 0 : doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].value)";
    }

    public abstract Script getScript();

    public String getRemainingLifeQuery() {
        return "("+getPriorityDateField("date.year")+"+20+("+getPriorityDateField("date.monthOfYear")+"-1)/12)+("+getTermExtensionField()+"/365.25)-"+getCurrentYearAndMonth();
    }


    public QueryBuilder getSortScript() {
        Script searchScript = getScript();
        if(searchScript==null) return null;
        System.out.println("Getting "+getName()+" sort script");
        return  QueryBuilders.functionScoreQuery(ScoreFunctionBuilders.scriptFunction(searchScript).setWeight(getWeight()))
                .boostMode(CombineFunction.AVG)
                .scoreMode(FiltersFunctionScoreQuery.ScoreMode.AVG);
    }

    public int getWeight() {
        return 1;
    }
}
