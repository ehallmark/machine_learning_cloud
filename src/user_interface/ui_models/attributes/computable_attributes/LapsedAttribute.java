package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;

import java.io.File;

/**
 * Created by Evan on 8/11/2017.
 */
public class LapsedAttribute extends ComputableFilingSetAttribute {
    public static final File FILE = new File(Constants.DATA_FOLDER+"lapsed_filings_set.jobj");
    public LapsedAttribute() {
        super(FILE);
    }

    @Override
    public String getName() {
        return Constants.LAPSED;
    }
}
