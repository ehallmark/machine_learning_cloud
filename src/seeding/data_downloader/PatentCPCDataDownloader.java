package seeding.data_downloader;

import seeding.Constants;

import java.io.File;

/**
 * Created by Evan on 8/13/2017.
 */
public class PatentCPCDataDownloader extends SingleFileDownloader {
    public PatentCPCDataDownloader() {
        super( new File("patent-cpc-dest/"), new File("patent-cpc-zip/"), Constants.PATENT_CPC_URL_CREATOR);
    }
}
