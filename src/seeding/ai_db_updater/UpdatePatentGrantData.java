package seeding.ai_db_updater;

import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.Constants;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdatePatentGrantData {

    public static void main(String[] args) {
        PatentGrantIterator patentIterator = Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        patentIterator.applyHandlers(new ClaimDataSAXHandler(), new InventionTitleSAXHandler(), new CitationSAXHandler(), new SAXHandler());
    }
}
