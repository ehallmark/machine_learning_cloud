package seeding.data_downloader;

import lombok.Getter;
import org.apache.commons.io.FileUtils;
import seeding.Constants;
import seeding.Database;
import seeding.ai_db_updater.iterators.DateIterator;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by Evan on 8/13/2017.
 */
public abstract class FileStreamDataDownloader implements DataDownloader, Serializable {
    private static final long serialVersionUID = 1L;
    @Getter
    protected String zipFilePrefix;
    protected String name;
    protected Set<String> finishedFiles;
    protected Set<String> failedFiles;
    protected transient DateIterator zipDownloader;
    public FileStreamDataDownloader(String name, Class<? extends DateIterator> zipDownloader) {
        // check for previous one
        FileStreamDataDownloader pastLife = load(name);
        if(pastLife==null) {
            this.name = name;
            this.finishedFiles = Collections.synchronizedSet(new HashSet<>());
            this.failedFiles = Collections.synchronizedSet(new HashSet<>());
        } else {
            this.name = name;
            this.finishedFiles = pastLife.finishedFiles;
            if(this.finishedFiles==null) this.finishedFiles = Collections.synchronizedSet(new HashSet<>());
            this.failedFiles = pastLife.failedFiles;
            if(this.failedFiles==null)this.failedFiles=Collections.synchronizedSet(new HashSet<>());
        }
        this.finishedFiles = this.finishedFiles.stream().map(f->{
            if(f.contains("/")) {
                return f.substring(f.lastIndexOf("/")+1,f.length());
            } else {
                return f;
            }
        }).collect(Collectors.toSet());
        try {
            this.zipDownloader = zipDownloader.newInstance();
            this.zipFilePrefix = this.zipDownloader.getZipFilePrefix();
        } catch(Exception e) {
            throw new RuntimeException("Error instantiating: "+zipDownloader.getName());
        }
    }

    @Override
    public synchronized void pullMostRecentData() {
        System.out.println("Pulling most recent data...");
        // pull zips only
        LocalDate dateToUse = null;
        try {
            //LocalDate date = LocalDate.parse(new File("2021-01-01").getName(), DateTimeFormatter.ISO_DATE);
            LocalDate date = LocalDate.parse(zipFileStream(file->true).sorted((f1,f2)->f2.getName().compareTo(f1.getName())).findFirst().orElse(new File("2005-01-01")).getName(), DateTimeFormatter.ISO_DATE);
            dateToUse = date;
        } catch(Exception e) {
            try {
                LocalDate date = zipFileStream(file->true).map(file->{
                    try {
                        return LocalDate.parse("20"+file.getName(), DateTimeFormatter.BASIC_ISO_DATE);
                    } catch(Exception e2) {
                        return null;
                    }
                }).filter(f->f!=null).sorted((f1,f2)->f2.compareTo(f1)).findFirst().orElse(null);
                if(date!=null) {
                    dateToUse = date;
                }
            } catch(Exception e2) {
                e2.printStackTrace();
                throw new RuntimeException("Unable to parse date from current files...");
            }
        }
        System.out.println("Date to start from: "+dateToUse);
        if(dateToUse!=null) zipDownloader.run(dateToUse,failedFiles);
    }

    public Stream<File> zipFileStream(Function<File,Boolean> orFilter) {
        return Arrays.stream(new File(zipFilePrefix).listFiles()==null?new File[]{}:new File(zipFilePrefix).listFiles()).filter(file->orFilter.apply(file)||!finishedFiles.contains(file.getName()));
    }

    public synchronized void save() {
        safeSaveFile(this,new File(Constants.DATA_FOLDER,Constants.DATA_DOWNLOADERS_FOLDER+name));
    }

    public synchronized void clearCache() {
        boolean deleted = new File(Constants.DATA_FOLDER, Constants.DATA_DOWNLOADERS_FOLDER+name).delete();
        if(!deleted) System.out.println("Error deleting cache for: "+name);
        finishedFiles.clear();
        failedFiles.clear();
        save();
        System.out.println("Cleared cache for: "+name);
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
            } else {
                if (file.getParentFile() != null){
                    file.getParentFile().mkdirs();
                }
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
                    ret = (FileStreamDataDownloader) Database.tryLoadObjectOld(file);
                    try {
                        ret = (FileStreamDataDownloader) Database.tryLoadObject(new File(file.getAbsolutePath() + "-backup"));
                    } catch (Exception e2) {
                        ret = null;
                    }
                } catch(Exception e3) {
                    e3.printStackTrace();
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
            failedFiles.add(file.getName());
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public abstract LocalDate dateFromFileName(String name);

    public synchronized void finishedIngestingFile(File file) {
        System.out.println("Finished file: "+file.getName());
        try {
            failedFiles.remove(file.getName());
        } catch(Exception e) {
            e.printStackTrace();
        }
        finishedFiles.add(file.getName());
    }
}
