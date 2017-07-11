package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.SAXFullTextHandler;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;


/**
 * Created by ehallmark on 1/3/17.
 */
public class UpdatePatentsAndApplicationsTable {
    public static void main(String[] args) throws Exception {
        PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        patentIterator.applyHandlers(new SAXFullTextHandler(PortfolioList.Type.patents));

        PatentGrantIterator applicationIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        applicationIterator.applyHandlers(new SAXFullTextHandler(PortfolioList.Type.applications));
    }

}