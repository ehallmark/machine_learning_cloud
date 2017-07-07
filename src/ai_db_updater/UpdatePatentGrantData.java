package ai_db_updater;

import ai_db_updater.handlers.InventionTitleSAXHandler;
import ai_db_updater.iterators.PatentGrantIterator;
import ai_db_updater.handlers.CitationSAXHandler;
import ai_db_updater.tools.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler());
        System.out.println("FINAL DATE: "+ iterator.startDate.toString());
    }
}
