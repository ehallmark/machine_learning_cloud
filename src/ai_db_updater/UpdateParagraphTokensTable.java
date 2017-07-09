package ai_db_updater;

import ai_db_updater.iterators.PatentGrantIterator;
import ai_db_updater.handlers.SAXHandler;


/**
 * Created by ehallmark on 1/3/17.
 */
public class UpdateParagraphTokensTable {
    public static void main(String[] args) throws Exception {
        PatentGrantIterator iterator = ai_db_updater.tools.Constants.DEFAULT_PATENT_GRANT_ITERATOR;
        iterator.applyHandlers(new SAXHandler());
    }

}