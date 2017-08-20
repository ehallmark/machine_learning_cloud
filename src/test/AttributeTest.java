package test;

import user_interface.ui_models.attributes.AbstractAttribute;
import user_interface.ui_models.attributes.computable_attributes.ComputableAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import user_interface.ui_models.attributes.hidden_attributes.AssigneeToAssetsMap;

import java.util.Collection;

/**
 * Created by Evan on 8/19/2017.
 */
public class AttributeTest {
    public static void main(String[] args) {
        AssetToAssigneeMap assetToAssigneeMap = new AssetToAssigneeMap();
        AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
        assetToAssigneeMap.save();
        assigneeToAssetsMap.getPatentDataMap().entrySet().stream().limit(5).forEach(e->{
            System.out.println(e.getKey()+": "+e.getValue());
        });
        assigneeToAssetsMap.getApplicationDataMap().entrySet().stream().limit(5).forEach(e->{
            System.out.println(e.getKey()+": "+e.getValue());
        });
    }
}
