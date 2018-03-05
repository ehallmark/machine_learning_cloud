package user_interface.server;

import java.io.File;

public class PlatformServerManager {






    public static void runBashProcess(String statement) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/bash", "-c", statement);
        Process process = ps.start();
        process.waitFor();
    }

    public static void runScriptProcess(File scriptFile) throws Exception {
        ProcessBuilder ps = new ProcessBuilder("/bin/sh", scriptFile.getAbsolutePath());
        Process process = ps.start();
        process.waitFor();
    }


    public static void performWeeklyCycle() {
        // stop server

        // setup weekly updates


        // restart server
    }


    public static void main(String[] args) {
        // set weekly cron job

        // everyWeek.runJob(performWeeklyCycle());
    }
}
