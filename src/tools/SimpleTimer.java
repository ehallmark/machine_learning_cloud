package tools;

/**
 * Created by ehallmark on 2/20/17.
 */
public class SimpleTimer {
    private long time;
    private long elapsedTime;

    public void start() {
        time=System.currentTimeMillis();
    }
    public void finish() {
        elapsedTime=System.currentTimeMillis()-time;
    }

    public long getElapsedTime() { return elapsedTime; }
}
