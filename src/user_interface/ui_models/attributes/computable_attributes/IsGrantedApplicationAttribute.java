package user_interface.ui_models.attributes.computable_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.util.Arrays;
import java.util.Collection;

/**
 * Created by ehallmark on 7/20/17.
 */
public class IsGrantedApplicationAttribute extends ComputableAttribute<Boolean> {
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    private static final FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

    public IsGrantedApplicationAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.BoolFalse));
    }

    @Override
    public QueryBuilder getQueryScope() {
        return QueryBuilders.termQuery(Constants.DOC_TYPE, PortfolioList.Type.applications.toString());
    }

    @Override
    public String getScope() {
        return PortfolioList.Type.applications.toString();
    }

    @Override
    public Boolean attributesFor(Collection<String> items, int limit, Boolean isApp) {
        String item = items.stream().findAny().get();
        if(assetToFilingMap.getPatentDataMap().containsKey(item) || !Database.isApplication(item)) return true; // is already a grant
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

}
