package user_interface.ui_models.attributes.hidden_attributes;

import seeding.Constants;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 8/11/2017.
 */
public class AssetToCPCMap extends HiddenAttribute<Set<String>> {
    @Override
    public Set<String> handleIncomingData(String name, Map<String,Object> allData, Map<String, Set<String>> myData, boolean isApp) {
        return null;
    }

    @Override
    public String getName() {
        return Constants.NAME+"_to_"+Constants.CPC_CODES;
    }
}
