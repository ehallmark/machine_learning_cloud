package user_interface.ui_models.attributes.script_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

/**
 * Created by ehallmark on 6/15/17.
 */
public class RemainingLifeAttribute extends AbstractScriptAttribute {

    public RemainingLifeAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.REMAINING_LIFE;
    }

    @Override
    public QueryBuilder getScriptQuery() {
        return null;
    }

    @Override
    public Script getScript() {
        long millisecondsToday = ZonedDateTime.now().toEpochSecond()*1000;
        return new Script(ScriptType.INLINE, "expression", "("+String.valueOf(millisecondsToday)+"-"+getPriorityDateField()+"+"+getTermExtensionMillis()+")/31536000000", new HashMap<>());
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

    private String getPriorityDateField() {
        return "(doc['"+Constants.PRIORITY_DATE+"'].empty ? doc['"+Constants.FILING_DATE+"'].date : doc['"+Constants.PRIORITY_DATE+"'].date)";
    }

    private String getTermExtensionMillis() {
        return "(doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].empty ? 0 : (doc['"+Constants.PATENT_TERM_ADJUSTMENT+"'].value * 86400000))";
    }
}
