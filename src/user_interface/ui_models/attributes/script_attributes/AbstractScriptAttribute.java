package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import seeding.Constants;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.ZonedDateTime;
import java.util.Collection;

/**
 * Created by ehallmark on 8/18/17.
 */
public abstract class AbstractScriptAttribute extends AbstractAttribute {
    protected static final long millisecondsToday = ZonedDateTime.now().toEpochSecond()*1000;

    public AbstractScriptAttribute(Collection<AbstractFilter.FilterType> filterTypes) {
        super(filterTypes);
    }

    protected String getPriorityDateField() {
        return "(doc['"+ Constants.PRIORITY_DATE+"'].empty ? doc['"+Constants.FILING_DATE+"'].date : doc['"+Constants.PRIORITY_DATE+"'].date)";
    }

    protected String getTermExtensionMillis() {
        return "(doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].empty ? 0 : (doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].value * 86400000))";
    }

    public abstract QueryBuilder getSortScript();
    public abstract Script getScript();

}
