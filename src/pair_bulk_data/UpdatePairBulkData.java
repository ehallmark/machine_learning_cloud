package pair_bulk_data;

import java.io.File;

/**
 * Created by Evan on 8/5/2017.
 */
public class UpdatePairBulkData {
    public static void main(String[] args) {
        FileIterator pairIterator = new FileIterator(new File("data/pair_data"));
        pairIterator.applyHandlers(new PAIRHandler());
    }
}
