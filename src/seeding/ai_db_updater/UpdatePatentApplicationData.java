package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.Constants;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentApplicationData {

    public static void main(String[] args) {
        PatentGrantIterator applicationIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        applicationIterator.applyHandlers(new AppClaimDataSAXHandler(), new ApplicationCitationSAXHandler(), new ApplicationInventionTitleSAXHandler(), new SAXHandler());
    }
}
