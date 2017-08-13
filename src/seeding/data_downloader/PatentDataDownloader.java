package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.PatentUSPTOIterator;
import user_interface.ui_models.portfolios.PortfolioList;

/**
 * Created by Evan on 8/13/2017.
 */
public class PatentDataDownloader extends FileStreamDataDownloader {
    public PatentDataDownloader() {
        super(PortfolioList.Type.patents.toString(), PatentUSPTOIterator.class, Constants.DEFAULT_START_DATE);
    }
}
