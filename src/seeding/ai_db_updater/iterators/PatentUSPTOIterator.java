package seeding.ai_db_updater.iterators;

import seeding.Constants;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

/**
 * Created by Evan on 8/13/2017.
 */
public class PatentUSPTOIterator extends IngestUSPTOIterator {
    public PatentUSPTOIterator() {
        super(Constants.PATENT_ZIP_FOLDER, Constants.GOOGLE_URL_CREATOR, Constants.USPTO_URL_CREATOR);
    }
}
