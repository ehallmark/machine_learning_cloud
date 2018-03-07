package user_interface.ui_models.attributes.computable_attributes.asset_graphs;

import seeding.Constants;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.AssetToRelatedAssetsMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.*;

/**
 * Created by Evan on 9/24/2017.
 */
public class RelatedAssetsAttribute extends AssetGraph {
    public RelatedAssetsAttribute() {
        super(false,3,new AssetToRelatedAssetsMap());
    }

    @Override
    public String getName() {
        return Constants.ALL_RELATED_ASSETS;
    }

    @Override
    public String getType() {
        return "keyword";
    }

    @Override
    public void initAndSave(boolean testing) {
        Map<String,Collection<String>> newPatentMap = Collections.synchronizedMap(new HashMap<>());
        Map<String,Collection<String>> newApplicationMap = Collections.synchronizedMap(new HashMap<>());

        newApplicationMap.putAll(dependentAttributes[0].getApplicationDataMap());
        newPatentMap.putAll(dependentAttributes[0].getPatentDataMap());

        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();
        FilingToAssetMap filingToAssetMap = new FilingToAssetMap();

        assetToFilingMap.getApplicationDataMap().entrySet().parallelStream().forEach(e->{
            Set<String> related = Collections.synchronizedSet(new HashSet<>());
            related.addAll(filingToAssetMap.getApplicationDataMap().getOrDefault(e.getValue(),Collections.emptyList()));
            related.addAll(filingToAssetMap.getPatentDataMap().getOrDefault(e.getValue(),Collections.emptyList()));
            related.addAll(newApplicationMap.getOrDefault(e.getKey(),Collections.emptyList()));
            related.add(e.getValue());
            related.remove(e.getKey());
            newApplicationMap.put(e.getKey(), related);
        });

        assetToFilingMap.getPatentDataMap().entrySet().parallelStream().forEach(e->{
            Set<String> related = Collections.synchronizedSet(new HashSet<>());
            related.addAll(filingToAssetMap.getApplicationDataMap().getOrDefault(e.getValue(),Collections.emptyList()));
            related.addAll(filingToAssetMap.getPatentDataMap().getOrDefault(e.getValue(),Collections.emptyList()));
            related.addAll(newPatentMap.getOrDefault(e.getKey(),Collections.emptyList()));
            related.add(e.getValue());
            related.remove(e.getKey());
            newPatentMap.put(e.getKey(), related);
        });

        // add additional dependent attributes
        ComputableAttribute<? extends Collection<String>> newAttribute = new ComputableAttribute<Collection<String>>(Collections.emptyList()) {
            @Override
            public String getName() {
                return null;
            }

            @Override
            public String getType() {
                return null;
            }

            @Override
            public AbstractFilter.FieldType getFieldType() {
                return null;
            }

            @Override
            public Map<String,Collection<String>> getPatentDataMap() {
                return newPatentMap;
            }

            @Override
            public Map<String,Collection<String>> getApplicationDataMap() {
                return newApplicationMap;
            }
        };


        dependentAttributes[0] = newAttribute;

        // call super
        super.initAndSave(testing);
    }
}
