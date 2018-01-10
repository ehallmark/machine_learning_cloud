package user_interface.ui_models.attributes.computable_attributes;

import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PriorityDateComputedAttribute extends ComputableAttribute<LocalDate> {
    private static final AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
    private static Map<String,LocalDate> priorityDateMap;
    private static final File priorityDateMapFile = new File(Constants.DATA_FOLDER+"patentPriorityDateByFilingMap.jobj");

    public PriorityDateComputedAttribute() {
        super(Arrays.asList(AbstractFilter.FilterType.Between));
    }

    @Override
    public LocalDate attributesFor(Collection<String> items, int limit, Boolean isApp) {
        String item = items.stream().findAny().get();

        String filing;
        if(isApp==null) filing = assetToFilingMap.getPatentDataMap().getOrDefault(item,assetToFilingMap.getApplicationDataMap().get(item));
        else if(isApp) filing = assetToFilingMap.getApplicationDataMap().get(item);
        else filing = assetToFilingMap.getPatentDataMap().get(item);

        if(filing==null) return null;

        if(priorityDateMap==null) {
            synchronized (PriorityDateComputedAttribute.class) {
                priorityDateMap = loadMap();
            }
        }

        return priorityDateMap.get(filing);
    }

    @Override
    public Map<String,LocalDate> getPatentDataMap() {
        return null;
    }

    @Override
    public Map<String,LocalDate> getApplicationDataMap() {
        return null;
    }

    @Override
    public LocalDate handleIncomingData(String item, Map<String, Object> data, Map<String,LocalDate> myData, boolean isApplication) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.PRIORITY_DATE;
    }

    @Override
    public String getType() {
        return "date";
    }


    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.Date;
    }


    public static void saveMap(Map<String,LocalDate> map) {
        priorityDateMap=map;
        Database.trySaveObject(map,priorityDateMapFile);
    }

    public static Map<String,LocalDate> loadMap() {
        if(priorityDateMap==null) {
            priorityDateMap = (Map<String, LocalDate>) Database.tryLoadObject(priorityDateMapFile);
        }
        return priorityDateMap;
    }
}
