package user_interface.ui_models.filters;

import lombok.NonNull;
import spark.Request;
import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToRelatedAssetsMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 6/17/2017.
 */
public class AbstractExcludeWithRelatedFilter extends AbstractExcludeFilter {
    public AbstractExcludeWithRelatedFilter(@NonNull AbstractAttribute attribute, FilterType filterType, FieldType fieldType, Collection<String> labels) {
        super(attribute, filterType, fieldType, labels==null?null:new ArrayList<>(labels));

    }

    @Override
    public AbstractFilter dup() {
        return new AbstractExcludeWithRelatedFilter(attribute,filterType,fieldType, labels==null?null:new ArrayList<>(labels));
    }

    @Override
    public void extractRelevantInformationFromParams(Request req) {
        super.extractRelevantInformationFromParams(req);
        AssetToRelatedAssetsMap assetToRelatedAssetsMap = new AssetToRelatedAssetsMap();
        if(labels!=null) {
            // expand
            labels = labels.stream().flatMap(label->{
                return Stream.of(
                        Stream.of(label),
                        assetToRelatedAssetsMap.getApplicationDataMap().getOrDefault(label, Collections.emptyList()).stream(),
                        assetToRelatedAssetsMap.getPatentDataMap().getOrDefault(label,Collections.emptyList()).stream()
                ).flatMap(s->s);
            }).distinct().collect(Collectors.toList());
        }
    }
}
