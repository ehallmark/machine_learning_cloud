package models.assignee.normalization.name_correction;

import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;

public class RenormalizeAssigneeAttribute {
    public static void main(String[] args) {
        AssetToAssigneeMap assetToAssigneeMap = new AssetToAssigneeMap();
        assetToAssigneeMap.getApplicationDataMap();
        assetToAssigneeMap.getPatentDataMap();
        assetToAssigneeMap.save();
    }
}
