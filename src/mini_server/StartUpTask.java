package mini_server;

import java.util.TimerTask;

/**
 * Created by ehallmark on 8/1/17.
 */
public class StartUpTask extends TimerTask {
    @Override
    public void run() {
        System.out.println("Server should start soon...");
        Server.keepOn.set(true);
    }
}
