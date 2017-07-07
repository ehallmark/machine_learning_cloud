package ai_db_updater.iterators;

import ai_db_updater.handlers.CustomHandler;

/**
 * Created by Evan on 7/5/2017.
 */
public interface WebIterator {
    void applyHandlers(CustomHandler... handlers);
}
