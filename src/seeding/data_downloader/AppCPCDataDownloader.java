package seeding.data_downloader;

import seeding.Constants;

import java.io.File;

/**
 * Created by Evan on 8/13/2017.
 */
public class AppCPCDataDownloader extends SingleFileDownloader {
    public AppCPCDataDownloader() {
        super( new File("app-cpc-dest/"), new File("app-cpc-zip/"), Constants.APP_CPC_URL_CREATOR);
    }
}
