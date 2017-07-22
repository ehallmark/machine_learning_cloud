package test;

import seeding.Constants;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateElasticSearch {

    public static void main(String[] args) {
        PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        patentIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.patents));

        PatentGrantIterator appIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        appIterator.applyHandlers(new ElasticSearchHandler(PortfolioList.Type.applications));
    }
}
