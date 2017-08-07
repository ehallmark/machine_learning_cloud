package pair_bulk_data;

import seeding.ai_db_updater.iterators.FileIterator;

import java.io.File;

/**
 * Created by Evan on 8/5/2017.
 */
public class UpdatePairBulkData {
    public static void main(String[] args) {
        FileIterator pairIterator = new FileIterator(new File("data/pair_data"),(dir, name) -> {
            try {
                return Integer.valueOf(name.substring(0,4)) >= 2005;
            } catch(Exception e) {
                return false;
            }
        });
        pairIterator.applyHandlers(new PAIRHandler());
    }
}
