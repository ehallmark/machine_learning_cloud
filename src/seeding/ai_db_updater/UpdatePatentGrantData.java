package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.InventionTitleSAXHandler;
import seeding.ai_db_updater.handlers.SAXFullTextHandler;
import seeding.ai_db_updater.handlers.SAXHandler;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.ai_db_updater.handlers.CitationSAXHandler;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        patentIterator.applyHandlers(new InventionTitleSAXHandler(), new CitationSAXHandler(), new SAXHandler(), new SAXFullTextHandler(PortfolioList.Type.patents));
    }
}
