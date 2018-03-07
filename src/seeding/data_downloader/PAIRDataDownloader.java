package seeding.data_downloader;

import seeding.Constants;

import java.io.File;

/**
 * Created by Evan on 8/13/2017.
 */
public class PAIRDataDownloader extends SingleFileDownloader {
    public PAIRDataDownloader() {
        super( new File("pair_data/"), new File("pair_data.zip"), Constants.PAIR_BULK_URL_CREATOR);
    }
}
