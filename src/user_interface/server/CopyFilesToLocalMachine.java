package user_interface.server;

import org.apache.commons.io.FileUtils;
import seeding.Database;

import java.io.File;

public class CopyFilesToLocalMachine {
    public static void main(String[] args) throws Exception{
        FileUtils.deleteDirectory(new File(Database.DATA_COPY_FOLDER));
        Database.setCopyDataFlag(true);
        SimilarPatentServer.loadStuff();
        SimilarPatentServer.awaitTermination();
        System.exit(0);
    }
}
