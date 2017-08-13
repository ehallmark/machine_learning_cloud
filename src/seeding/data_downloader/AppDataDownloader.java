package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.IngestUSPTOAssignmentIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOIterator;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 8/13/2017.
 */
public class AppDataDownloader extends FileStreamDataDownloader {
    public AppDataDownloader() {
        super(PortfolioList.Type.applications.toString(), new IngestUSPTOIterator(Constants.APP_ZIP_FOLDER, Constants.GOOGLE_APP_URL_CREATOR, Constants.USPTO_APP_URL_CREATOR), Constants.DEFAULT_START_DATE);
    }
}
