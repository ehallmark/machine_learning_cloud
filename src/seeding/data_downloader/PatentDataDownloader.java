package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOIterator;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 8/13/2017.
 */
public class PatentDataDownloader extends FileStreamDataDownloader {
    public PatentDataDownloader() {
        super(PortfolioList.Type.patents.toString(), new IngestUSPTOIterator(Constants.PATENT_ZIP_FOLDER, Constants.GOOGLE_URL_CREATOR, Constants.USPTO_URL_CREATOR), Constants.DEFAULT_START_DATE);
    }
}
