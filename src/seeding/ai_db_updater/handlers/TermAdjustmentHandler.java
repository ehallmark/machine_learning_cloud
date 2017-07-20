package seeding.ai_db_updater.handlers;

import seeding.Database;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by ehallmark on 7/12/17.
 */
public class TermAdjustmentHandler implements LineHandler {
    protected Map<String,Integer> patentToTermAdjustmentMap = new HashMap<>();

    @Override
    public void save() {
        Database.saveObject(patentToTermAdjustmentMap,Database.patentToTermAdjustmentMap);
    }

    @Override
    public void handleLine(String line) {
        String[] row = line.split(",");
        if(row.length>4) {
            String app = row[0];
            app = app.substring(0,2)+"/"+app.substring(2);
            String termAdjustment = row[4];
            try {
                int ta = Integer.valueOf(termAdjustment);
                if(ta>0) {
                    patentToTermAdjustmentMap.put(app, ta);
                }
            } catch(Exception e) {

            }
        }

    }
}
