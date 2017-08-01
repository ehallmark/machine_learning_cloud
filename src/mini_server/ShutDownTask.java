package mini_server;

import java.util.TimerTask;

/**
 * Created by ehallmark on 8/1/17.
 */
public class ShutDownTask extends TimerTask {
    @Override
    public void run() {
        System.out.println("Stopping server...");
        Server.keepOn.set(false);
    }
}
