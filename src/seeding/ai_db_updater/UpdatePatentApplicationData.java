package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.ApplicationCitationSAXHandler;
import seeding.ai_db_updater.handlers.ApplicationInventionTitleSAXHandler;
import seeding.ai_db_updater.handlers.SAXFullTextHandler;
import seeding.ai_db_updater.handlers.SAXHandler;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentApplicationData {

    public static void main(String[] args) {
        PatentGrantIterator applicationIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        applicationIterator.applyHandlers(new ApplicationCitationSAXHandler(), new ApplicationInventionTitleSAXHandler(), new SAXHandler(), new SAXFullTextHandler(PortfolioList.Type.applications));
    }
}
