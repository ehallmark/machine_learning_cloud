package user_interface.ui_models.filters;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import user_interface.ui_models.attributes.AbstractAttribute;

import java.util.Collections;

public class AssetDedupFilter extends AbstractBooleanExcludeFilter {
    public static final String NAME = "assetDedupFilter";
    public AssetDedupFilter() {
        super(new AssetDedupAttribute(), FilterType.BoolFalse);
    }

    @Override
    public QueryBuilder getFilterQuery() {
        return QueryBuilders.matchAllQuery();
    }

    @Override
    protected String transformAttributeScript(String attributeScript) {
        throw new UnsupportedOperationException("transformAttributeScript");
    }

    @Override
    public String getId() {
        return NAME;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getFullPrerequisite() {
        return NAME;
    }

    @Override
    public String getFullName() {
        return NAME;
    }

    @Override
    public String getPrerequisite() {
        return NAME;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter dup() {
        return new AssetDedupFilter();
    }


}

class AssetDedupAttribute extends AbstractAttribute {
    public AssetDedupAttribute() {
        super(Collections.emptyList());
    }

    @Override
    public String getName() {
        return AssetDedupFilter.NAME;
    }

    @Override
    public String getType() {
        return "boolean";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Boolean;
    }
}
