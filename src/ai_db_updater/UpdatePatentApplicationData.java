package ai_db_updater;

import ai_db_updater.handlers.ApplicationCitationSAXHandler;
import ai_db_updater.handlers.ApplicationInventionTitleSAXHandler;
import ai_db_updater.iterators.PatentGrantIterator;
import ai_db_updater.tools.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentApplicationData {

    public static void main(String[] args) {
        PatentGrantIterator iterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        iterator.applyHandlers(new ApplicationCitationSAXHandler(), new ApplicationInventionTitleSAXHandler());
        System.out.println("FINAL DATE: "+ iterator.startDate.toString());
    }
}
