package seeding.data_downloader;

import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.iterators.DateIterator;
import seeding.ai_db_updater.iterators.IngestUSPTOIterator;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDate;
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
    protected Set<String> finishedFiles = new HashSet<>();
    protected LocalDate lastUpdatedDate;
    protected transient DateIterator zipDownloader;
    public FileStreamDataDownloader(String name, Class<? extends DateIterator> zipDownloader, LocalDate lastUpdatedDate) {
        // check for previous one
        FileStreamDataDownloader pastLife = load(name);
        if(pastLife==null) {
            this.lastUpdatedDate = lastUpdatedDate;
            this.name = name;
            this.finishedFiles = new HashSet<>();
        } else {
            this.lastUpdatedDate = pastLife.lastUpdatedDate;
            this.name = pastLife.name;
            this.finishedFiles = pastLife.finishedFiles;
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
        zipDownloader.run(lastUpdatedDate);
    }

    public Stream<File> zipFileStream() {
        return Arrays.stream(new File(zipFilePrefix).listFiles()).filter(file->!finishedFiles.contains(file.getAbsolutePath()));
    }

    public synchronized void save() {
        this.lastUpdatedDate = LocalDate.now().minusDays(1);
        Database.trySaveObject(this,new File(Constants.DATA_FOLDER,Constants.DATA_DOWNLOADERS_FOLDER+name));
    }

    private synchronized static FileStreamDataDownloader load(String name) {
        File file = new File(Constants.DATA_FOLDER+Constants.DATA_DOWNLOADERS_FOLDER+name);
        if(file.exists()) {
            return (FileStreamDataDownloader) Database.tryLoadObject(file);
        } else {
            return null;
        }
    }

    public synchronized void finishedIngestingFile(File file) {
        System.out.println("Finished file: "+file.getName());
        finishedFiles.add(file.getAbsolutePath());
    }
}
