package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;

import java.io.File;

/**
 * Created by ehallmark on 6/15/17.
 */
public class ReinstatedAttribute extends ComputableFilingSetAttribute {
    public static final File FILE = new File(Constants.DATA_FOLDER+"reinstated_filings_set.jobj");

    public ReinstatedAttribute() {
        super(FILE);
    }

    @Override
    public String getName() {
        return Constants.REINSTATED;
    }

}
