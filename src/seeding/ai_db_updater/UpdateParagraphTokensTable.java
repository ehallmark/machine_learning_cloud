package seeding.ai_db_updater;

import seeding.Constants;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import seeding.ai_db_updater.handlers.SAXHandler;


/**
 * Created by ehallmark on 1/3/17.
 */
public class UpdateParagraphTokensTable {
    public static void main(String[] args) throws Exception {
        PatentGrantIterator patentIterator = seeding.Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        patentIterator.applyHandlers(new SAXHandler());

        PatentGrantIterator appIterator = Constants.DEFAULT_PATENT_APPLICATION_ITERATOR;
        appIterator.applyHandlers(new SAXHandler());
    }

}