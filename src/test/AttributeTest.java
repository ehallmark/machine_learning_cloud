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
        assetToAssigneeMap.getApplicationDataMap();
        assetToAssigneeMap.getPatentDataMap();
        AssigneeToAssetsMap assigneeToAssetsMap = new AssigneeToAssetsMap();
        assetToAssigneeMap.save();
        System.out.println("Size of app map: "+assigneeToAssetsMap.getApplicationDataMap().size());
        System.out.println("Size of patent map: "+assigneeToAssetsMap.getApplicationDataMap().size());
        assigneeToAssetsMap.getPatentDataMap().entrySet().stream().limit(5).forEach(e->{
            System.out.println(e.getKey()+": "+e.getValue());
        });
        assigneeToAssetsMap.getApplicationDataMap().entrySet().stream().limit(5).forEach(e->{
            System.out.println(e.getKey()+": "+e.getValue());
        });
    }
}
