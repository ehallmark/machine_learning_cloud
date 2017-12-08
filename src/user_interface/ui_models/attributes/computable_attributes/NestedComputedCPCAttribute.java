package user_interface.ui_models.attributes.computable_attributes;

import cpc_normalization.CPC;
import cpc_normalization.CPCHierarchy;
import lombok.NonNull;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.filters.AbstractFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 12/8/17.
 */
public class NestedComputedCPCAttribute extends ComputableCPCAttribute<List<Map<String,Object>>> {
    private static final CPCHierarchy hierarchy = new CPCHierarchy();
    private static Map<String,String> cpcToTitleMap;
    public NestedComputedCPCAttribute() {
        super(Collections.emptyList());
        if(hierarchy.getLabelToCPCMap()==null) hierarchy.loadGraph();
        if(cpcToTitleMap==null) cpcToTitleMap= Database.getClassCodeToClassTitleMap();
    }

    @Override
    public boolean isDisplayable() {
        return false;
    }

    @Override
    public String getName() {
        return Constants.NESTED_CPC_CODES;
    }

    @Override
    public String getType() {
        return "nested";
    }

    @Override
    public AbstractFilter.FieldType getFieldType() {
        return AbstractFilter.FieldType.NestedObject;
    }

    @Override
    protected List<Map<String, Object>> attributesforCPCsHelper(@NonNull Collection<String> cpcs) {
        return cpcs.stream().map(cpcLabel->{
            CPC cpc = hierarchy.getLabelToCPCMap().get(cpcLabel);
            if(cpc!=null) {
                Map<String, Object> data = new HashMap<>();
                int nParts = cpc.getNumParts();
                if(nParts>0) {
                    data.put(Constants.CPC_CODES, cpcLabel);
                    data.put(Constants.CPC_SECTION, cpc.getParts()[0]);
                    if(nParts>1) {
                        data.put(Constants.CPC_CLASS, cpc.getParts()[1]);
                        if(nParts>2) {
                            data.put(Constants.CPC_SUBCLASS, cpc.getParts()[2]);
                            if(nParts>3) {
                                data.put(Constants.CPC_MAIN_GROUP, cpc.getParts()[3]);
                            }
                            if(nParts>4) {
                                data.put(Constants.CPC_SUBGROUP, cpc.getParts()[4]);
                            }
                        }
                    }
                    String title = cpcToTitleMap.get(cpcLabel);
                    if(title!=null) {
                        data.put(Constants.CPC_TITLE,title);
                    }
                    return data;
                }
            }
            return null;
        }).filter(data->data!=null).collect(Collectors.toList());
    }
}
