package user_interface.ui_models.filters;

import lombok.NonNull;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import seeding.google.elasticsearch.Attributes;
import seeding.google.elasticsearch.attributes.AssetAttribute;
import seeding.google.elasticsearch.attributes.CountryCodeToCountryMap;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.dataset_lookup.TermsLookupAttribute;

import java.util.*;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractIncludeAssetFilter extends AbstractIncludeFilter {
    protected boolean isApplication;
    protected String assetPrefix;
    protected Map<String, List<String>> assetFieldToAssetsMap;
    public AbstractIncludeAssetFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType, fieldType, labels);
        if(!(attribute instanceof AssetAttribute)) throw new RuntimeException("Illegal filter type for asset attribute: "+attribute.getFullName());
        this.isApplication = ((AssetAttribute) attribute).isApplication();
        this.assetPrefix = ((AssetAttribute) attribute).getAssetPrefix();
        this.assetFieldToAssetsMap = buildAssetFieldToAssetsMap(labels);
    }


    protected Map<String,List<String>> buildAssetFieldToAssetsMap(Collection<String> labels) {
        if (labels==null) return Collections.emptyMap();
        Map<String, List<String>> assetFieldToAssetsMap = new HashMap();
        for(String label : labels) {
            if(label.length()>2) {
                String suffix = label.substring(label.length()-2);
                String kindCode = null;
                String asset;
                if(Character.isAlphabetic(suffix.charAt(0))) {
                    kindCode = suffix;
                    asset = label.substring(0, label.length()-2);
                } else if(Character.isAlphabetic(suffix.charAt(1))) {
                    kindCode = suffix.substring(1);
                    asset = label.substring(0, label.length()-1);
                } else {
                    asset = label;
                }
                String countryCode = null;
                String prefix = label.substring(0, 2);
                if(asset.length()>2 && CountryCodeToCountryMap.ALL_COUNTRIES.contains(prefix.toUpperCase())) {
                    countryCode = prefix;
                    asset = asset.substring(2);
                }
                String field;
                if (isApplication) {
                    if (countryCode!=null) {
                        field = Attributes.APPLICATION_NUMBER_FORMATTED_WITH_COUNTRY;
                        asset = countryCode + asset;
                    } else {
                        field = Attributes.APPLICATION_NUMBER_FORMATTED;
                    }
                } else {
                    if (countryCode != null && kindCode != null) {
                        field = Attributes.PUBLICATION_NUMBER_FULL;
                        asset = countryCode + asset + kindCode;
                    } else if (countryCode != null) {
                        field = Attributes.PUBLICATION_NUMBER_WITH_COUNTRY;
                        asset = countryCode + asset;
                    } else {
                        field = Attributes.PUBLICATION_NUMBER;
                    }
                }
                assetFieldToAssetsMap.putIfAbsent(field, new ArrayList<>());
                assetFieldToAssetsMap.get(field).add(asset);
            }
        }
        return assetFieldToAssetsMap;
    }

    @Override
    public AbstractFilter dup() {
        return new AbstractIncludeAssetFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public QueryBuilder getFilterQuery() {
        if (attribute instanceof TermsLookupAttribute) {
            throw new RuntimeException("Unable to use 'Include Asset Filter' for attribute: " + getFullPrerequisite() + ". Reason: Currently not supported.");
        }
        if(minimumShouldMatch<=1 && !(attribute instanceof TermsLookupAttribute)) {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            for (Map.Entry<String,List<String>> entry : assetFieldToAssetsMap.entrySet()) {
                String preReq = assetPrefix+entry.getKey();
                boolQueryBuilder = boolQueryBuilder.should(QueryBuilders.termsQuery(preReq, entry.getValue()));
            }
            return boolQueryBuilder;
        } else {
            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().minimumShouldMatch(this.minimumShouldMatch);
            for (Map.Entry<String,List<String>> entry : assetFieldToAssetsMap.entrySet()) {
                String preReq = assetPrefix+entry.getKey();
                for(String label : entry.getValue()) {
                    boolQueryBuilder = boolQueryBuilder.should(QueryBuilders.termQuery(preReq, label));
                }
            }
            return boolQueryBuilder;
        }
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        super.extractRelevantInformationFromParams(req);
        if(labels!=null) {
            this.assetFieldToAssetsMap = buildAssetFieldToAssetsMap(labels);
        }
    }
}
