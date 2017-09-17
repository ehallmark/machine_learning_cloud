package seeding.data_downloader;

import seeding.Constants;
import seeding.ai_db_updater.iterators.AppUSPTOIterator;
import user_interface.ui_models.portfolios.PortfolioList;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Created by Evan on 8/13/2017.
 */
public class AppDataDownloader extends FileStreamDataDownloader {
    public AppDataDownloader() {
        super(PortfolioList.Type.applications.toString(), AppUSPTOIterator.class, Constants.DEFAULT_START_DATE);
    }

    @Override
    public LocalDate dateFromFileName(String name) {
        return LocalDate.parse(name, DateTimeFormatter.ISO_DATE);
    }

}
