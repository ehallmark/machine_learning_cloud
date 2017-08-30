package user_interface.ui_models.attributes;

import seeding.Constants;

import java.util.Arrays;

/**
 * Created by Evan on 5/9/2017.
 */
public class RelatedDocumentsNestedAttribute extends NestedAttribute {

    public RelatedDocumentsNestedAttribute() {
        super(Arrays.asList(new AssetNumberAttribute(), new DocKindAttribute(), new CountryAttribute(), new RelationTypeAttribute()), Constants.NAME);
    }

    @Override
    public String getName() {
        return Constants.PATENT_FAMILY;
    }


}
