package seeding.ai_db_updater.iterators;

import seeding.Constants;

/**
 * Created by Evan on 8/13/2017.
 */
public class AppUSPTOIterator extends IngestUSPTOIterator {
    public AppUSPTOIterator() {
        super(Constants.APP_ZIP_FOLDER, Constants.GOOGLE_APP_URL_CREATOR, Constants.USPTO_APP_URL_CREATOR);
    }
}
