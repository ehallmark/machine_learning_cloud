package user_interface.ui_models.attributes.computable_attributes;

import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.filters.AbstractFilter;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class TermAdjustmentAttribute extends ComputableAttribute<Integer> {
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    private static Map<String,Integer> termAdjustmentMap;
    private static final File termAdjustmentMapFile = new File(Constants.DATA_FOLDER+"patentTermAdjustmentsByFilingMap.jobj");

    public TermAdjustmentAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public Integer attributesFor(Collection<String> items, int limit, Boolean isApp) {
        String item = items.stream().findAny().get();

        String filing;
        if(isApp==null) filing = assetToFilingMap.getPatentDataMap().getOrDefault(item,assetToFilingMap.getApplicationDataMap().get(item));
        else if(isApp) filing = assetToFilingMap.getApplicationDataMap().get(item);
        else filing = assetToFilingMap.getPatentDataMap().get(item);

        if(filing==null) return null;

        if(termAdjustmentMap==null) {
            synchronized (TermAdjustmentAttribute.class) {
                termAdjustmentMap = loadMap();
            }
        }

        return termAdjustmentMap.get(filing);
    }

    @Override
    public Map<String,Integer> getPatentDataMap() {
        return null;
    }

    @Override
    public Map<String,Integer> getApplicationDataMap() {
        return null;
    }

    @Override
    public Integer handleIncomingData(String item, Map<String, Object> data, Map<String,Integer> myData, boolean isApplication) {
        return null;
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


    public static void saveMap(Map<String,Integer> map) {
        termAdjustmentMap=map;
        Database.trySaveObject(map,termAdjustmentMapFile);
    }

    public static Map<String,Integer> loadMap() {
        return (Map<String,Integer>) Database.tryLoadObject(termAdjustmentMapFile);
    }
}
