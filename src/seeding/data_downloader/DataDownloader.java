package seeding.data_downloader;

import java.io.Serializable;

/**
 * Created by Evan on 8/13/2017.
 */
public interface DataDownloader extends Serializable {
    void pullMostRecentData();
}
