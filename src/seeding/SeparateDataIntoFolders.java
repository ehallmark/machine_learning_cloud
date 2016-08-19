package seeding;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 8/19/16.
 */
public class SeparateDataIntoFolders {
    public static void main(String[] args) throws Exception {
        File folder = new File(Constants.RAW_PATENT_DATA_FOLDER);
        int maxPerFolder = 1000;
        AtomicBoolean done = new AtomicBoolean(false);
        List<File> files;
        Stack<File> foldersToSearch = new Stack<>();
        foldersToSearch.add(folder);
        while(!done.get() && !foldersToSearch.isEmpty()) {
            File currentFolder = foldersToSearch.pop();
            foldersToSearch.addAll(recursionHelper(maxPerFolder, currentFolder, Arrays.asList(currentFolder.listFiles()).iterator()));
            files = Arrays.asList(folder.listFiles());
            done.set(true);
            files.forEach(f -> {
                if (folder.isFile()) done.set(false);
            });
        }
    }

    private static List<File> recursionHelper(int maxPerFolder, File currentFolder, Iterator<File> baseFiles) throws IOException {
        List<File> files = new ArrayList<>(maxPerFolder);
        for(int i = 0; i < maxPerFolder; i++) {
            File subFolder = (new File(currentFolder.getAbsolutePath()+"/"+i));
            subFolder.mkdirs();
            files.add(subFolder);
            AtomicInteger cnt = new AtomicInteger(0);
            while(cnt.getAndIncrement() < maxPerFolder && baseFiles.hasNext()) {
                File f = baseFiles.next();
                if(!f.isDirectory()) {
                    FileUtils.moveFileToDirectory(f,subFolder,false);
                }
            }
        }
        return files;
    }


}
