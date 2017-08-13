package seeding.data_downloader;

import lombok.Getter;
import models.classification_models.WIPOHelper;
import net.lingala.zip4j.core.ZipFile;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Created by Evan on 8/13/2017.
 */
public abstract class SingleFileDownloader implements DataDownloader {
    @Getter
    protected File destinationFile;
    protected File zipFile;
    protected UrlCreator urlCreator;
    public SingleFileDownloader(File destinationFile, File zipFile, UrlCreator urlCreator) {
        this.destinationFile=destinationFile;
        this.zipFile=zipFile;
        this.urlCreator=urlCreator;
    }
    @Override
    public void pullMostRecentData() {
        // pull and unzip
        boolean found = false;
        LocalDate date = LocalDate.now();
        try {
            if(destinationFile!=null&&destinationFile.exists()) {
                destinationFile.delete();
            }
            if(zipFile!=null&&zipFile.exists()) {
                zipFile.delete();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
        while (!found) {
            try {
                String url = urlCreator.create(date);
                URL website = new URL(url);
                System.out.println("Trying: " + website.toString());
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());

                File downloadFile = zipFile;
                if(zipFile==null) downloadFile = destinationFile;
                FileOutputStream fos = new FileOutputStream(downloadFile);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();
                if(zipFile != null) new ZipFile(zipFile).extractAll(destinationFile.getAbsolutePath());

                found = true;
            } catch (Exception e) {
                //e.printStackTrace();
                System.out.println("Not found");
            }
            date = date.minusDays(1);
            if (date.isBefore(LocalDate.now().minusYears(3)))
                throw new RuntimeException("Url does not work for class: "+this.getClass().getName());
        }

        // clean up
        try {
            if(destinationFile!=null&&destinationFile.exists()) {
                destinationFile.delete();
            }
            if(zipFile!=null&&zipFile.exists()) {
                zipFile.delete();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}
