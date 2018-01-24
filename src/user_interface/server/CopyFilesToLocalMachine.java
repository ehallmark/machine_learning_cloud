package user_interface.server;

import seeding.Database;

public class CopyFilesToLocalMachine {
    public static void main(String[] args) throws Exception{
        Database.setCopyDataFlag(true);
        SimilarPatentServer.loadStuff();
        SimilarPatentServer.awaitTermination();
        System.exit(0);
    }
}
