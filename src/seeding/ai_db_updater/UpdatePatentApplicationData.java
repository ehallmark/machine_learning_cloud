package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.ApplicationCitationSAXHandler;
import seeding.ai_db_updater.handlers.ApplicationInventionTitleSAXHandler;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentApplicationData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        iterator.applyHandlers(new ApplicationCitationSAXHandler(), new ApplicationInventionTitleSAXHandler());
    }
}
