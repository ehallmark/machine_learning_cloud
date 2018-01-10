package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.util.Arrays;

/**
 * Created by ehallmark on 7/20/17.
 */
public class TermAdjustmentAttribute extends ComputableFilingAttribute<Integer> {
    private static final File termAdjustmentMapFile = new File(Constants.DATA_FOLDER+"patentTermAdjustmentsByFilingMap.jobj");
    public TermAdjustmentAttribute() {
        super(termAdjustmentMapFile,Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public String getName() {
        return Constants.PATENT_TERM_ADJUSTMENT;
    }

    @Override
    public String getType() {
        return "integer";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Integer;
    }

}
