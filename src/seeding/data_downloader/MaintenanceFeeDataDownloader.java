package seeding.data_downloader;

import lombok.Getter;
import models.classification_models.WIPOHelper;
import net.lingala.zip4j.core.ZipFile;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Evan on 8/13/2017.
 */
public class MaintenanceFeeDataDownloader extends SingleFileDownloader {
    public MaintenanceFeeDataDownloader() {
        super(new File("maintenance-dest/"),new File("maintenance-zip/"), Constants.MAINTENANCE_FEE_URL_CREATOR);
    }
}
