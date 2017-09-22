package seeding.data_downloader;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.iterators.DateIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOIterator;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by Evan on 8/13/2017.
 */
public abstract class FileStreamDataDownloader implements DataDownloader, Serializable {
    private static final long serialVersionUID = 1l;
    @Getter
    protected String zipFilePrefix;
    protected String name;
    protected Set<String> finishedFiles;
    protected Set<LocalDate> failedDates;
    protected LocalDate lastUpdatedDate;
    protected transient DateIterator zipDownloader;
    public FileStreamDataDownloader(String name, Class<? extends DateIterator> zipDownloader, LocalDate lastUpdatedDate) {
        // check for previous one
        FileStreamDataDownloader pastLife = load(name);
        if(pastLife==null) {
            this.lastUpdatedDate = lastUpdatedDate;
            this.name = name;
            this.finishedFiles = new HashSet<>();
            this.failedDates = new HashSet<>();
        } else {
            this.lastUpdatedDate = pastLife.lastUpdatedDate;
            this.name = name;
            this.finishedFiles = pastLife.finishedFiles;
            this.failedDates = pastLife.failedDates;
            if(this.failedDates==null)this.failedDates=new HashSet<>();
        }
        try {
            this.zipDownloader = zipDownloader.newInstance();
            this.zipFilePrefix = this.zipDownloader.getZipFilePrefix();
        } catch(Exception e) {
            throw new RuntimeException("Error instantiating: "+zipDownloader.getName());
        }
    }

    @Override
    public synchronized void pullMostRecentData() {
        // pull zips only
        LocalDate dateToUse = lastUpdatedDate;
        try {
            LocalDate date = LocalDate.parse(zipFileStream().sorted((f1,f2)->f2.getName().compareTo(f1.getName())).findFirst().orElse(null).getName(), DateTimeFormatter.ISO_DATE);
            dateToUse = date;
        } catch(Exception e) {

        }
        zipDownloader.run(dateToUse,failedDates);
    }

    public Stream<File> zipFileStream() {
        return Arrays.stream(new File(zipFilePrefix).listFiles()).filter(file->!finishedFiles.contains(file.getAbsolutePath()));
    }

    public synchronized void save() {
        this.lastUpdatedDate = LocalDate.now().minusDays(1);
        safeSaveFile(this,new File(Constants.DATA_FOLDER,Constants.DATA_DOWNLOADERS_FOLDER+name));
    }

    protected static void safeSaveFile(Object obj, File file) {
        try {
            File backup;
            if(file.exists()) {
                backup = new File(file.getAbsolutePath()+"-backup");
                if(backup.exists()) backup.delete();
                backup = new File(backup.getAbsolutePath());
                // copy to backup
                FileUtils.copyFile(file, backup);
            }
            // write contents
            Database.trySaveObject(obj, file);

        } catch(Exception e) {
            e.printStackTrace();
            System.out.println("  ... While saving: "+file.getName());
        }
    }

    private synchronized static FileStreamDataDownloader load(String name) {
        File file = new File(Constants.DATA_FOLDER+Constants.DATA_DOWNLOADERS_FOLDER+name);
        if(file.exists()) {
            FileStreamDataDownloader ret;
            try {
                ret = (FileStreamDataDownloader) Database.tryLoadObject(file);
            } catch(Exception e) {
                try {
                    ret = (FileStreamDataDownloader) Database.tryLoadObject(new File(file.getAbsolutePath()+"-backup"));
                } catch(Exception e2) {
                    ret = null;
                }
            }
            return ret;
        } else {
            return null;
        }
    }

    public synchronized void errorOnFile(File file) {
        System.out.println("Error on file: "+file.getName());
        try {
            failedDates.add(dateFromFileName(file.getName()));
        } catch(DateTimeException e) {

        }
    }

    public abstract LocalDate dateFromFileName(String name);

    public synchronized void finishedIngestingFile(File file) {
        System.out.println("Finished file: "+file.getName());
        try {
            failedDates.remove(dateFromFileName(file.getName()));
        } catch(Exception e) {

        }
        finishedFiles.add(file.getAbsolutePath());
    }
}
