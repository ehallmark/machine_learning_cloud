package user_interface.ui_models.attributes.computable_attributes;

import j2html.tags.Tag;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import seeding.Constants;
import seeding.Database;
import spark.Request;
import user_interface.ui_models.attributes.AssetNumberAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.attributes.script_attributes.AbstractScriptAttribute;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class IsGrantedAttribute extends ComputableAttribute<Boolean> {
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    private static final FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

    public IsGrantedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse, AbstractFilter.FilterType.BoolTrue));
    }

    @Override
    public Boolean attributesFor(Collection<String> items, int limit) {
        String item = items.stream().findAny().get();
        if(assetToFilingMap.getPatentDataMap().containsKey(item)) return true; // is already a grant
        String filing = assetToFilingMap.getApplicationDataMap().get(item);
        if(filing == null) return null; // no info
        if(filingToAssetMap.getPatentDataMap().containsKey(filing)) return true; // has a grant
        return false;
    }

    @Override
    public String getName() {
        return Constants.GRANTED;
    }

    @Override
    public String getType() {
        return "boolean";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }

    @Override // TODO Implement!!
    public boolean isNotYetImplemented() {
        return true;
    }

}
