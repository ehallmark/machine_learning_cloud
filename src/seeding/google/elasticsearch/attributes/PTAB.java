package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;
import user_interface.ui_models.attributes.NestedAttribute;

import java.util.Arrays;

public class PTAB extends NestedAttribute {
    public PTAB() {
        super(Arrays.asList(new PtabAppealNo(), new PtabCaseName(), new PtabCaseStatus(), new PtabCaseText(), new PtabCaseType(), new PtabInterferenceNo(), new PtabInventorFirstName(), new PtabInventorLastName(), new PtabMailedDate()),true);
    }

        @Override
    public String getName() {
        return Attributes.PTAB;
    }
}
