package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.InventionTitleSAXHandler;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.ai_db_updater.handlers.CitationSAXHandler;
import seeding.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler());
    }
}
