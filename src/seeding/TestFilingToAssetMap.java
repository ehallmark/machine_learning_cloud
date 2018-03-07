package seeding;

import seeding.ai_db_updater.UpdateAssetGraphs;
import user_interface.ui_models.attributes.computable_attributes.IsGrantedApplicationAttribute;
import user_interface.ui_models.attributes.hidden_attributes.AssetToFilingMap;
import user_interface.ui_models.attributes.hidden_attributes.FilingToAssetMap;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by Evan on 12/16/2017.
 */
public class TestFilingToAssetMap {
    public static void main(String[] args) throws Exception {

        AssetToFilingMap assetToFilingMap = new AssetToFilingMap();

        IsGrantedApplicationAttribute isGrantedApplicationAttribute = new IsGrantedApplicationAttribute();

        AtomicLong missing = new AtomicLong(0);
        AtomicLong total = new AtomicLong(0);
        assetToFilingMap.getApplicationDataMap().entrySet().forEach(e->{
            String item = e.getKey();
            Boolean granted = isGrantedApplicationAttribute.attributesFor(Arrays.asList(item),1,true);
            if(granted==null) {
                System.out.println("missing");
                missing.getAndIncrement();
            }
            total.getAndIncrement();
            if(total.get()%10000==9999) {
                System.out.print("-");
            }
        });

        AtomicLong wrong = new AtomicLong(0);
        assetToFilingMap.getPatentDataMap().entrySet().forEach(e->{
            String item = e.getKey();
            Boolean granted = isGrantedApplicationAttribute.attributesFor(Arrays.asList(item),1,false);
            if(granted==null) {
                System.out.println("missing");
                missing.getAndIncrement();
            } else if(!granted) {
                System.out.println("wrong");
                wrong.getAndIncrement();
            }
            total.getAndIncrement();
            if(total.get()%10000==9999) {
                System.out.print("-");
            }
        });

        System.out.println("Total: "+total.get());
        System.out.println("Missing: "+missing.get());
        System.out.println("Wrong: "+wrong.get());
    }
}
