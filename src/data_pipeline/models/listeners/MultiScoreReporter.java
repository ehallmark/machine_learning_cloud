package data_pipeline.models.listeners;

import data_pipeline.optimize.nn_optimization.MultiLayerNetworkWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by ehallmark on 11/15/17.
 */
public class MultiScoreReporter {
    private AtomicInteger time;
    private int numNetworksToListenTo;
    private List<Message> backlog;
    private int displayTopN;
    public MultiScoreReporter(int numNetworksToListenTo, int displayTopN) {
        this.numNetworksToListenTo=numNetworksToListenTo;
        this.backlog=new ArrayList<>(numNetworksToListenTo);
        this.displayTopN=displayTopN;
        this.time=new AtomicInteger(0);
    }

    public void addToCurrentReport(String message, double bestScore) {
        backlog.add(new Message(message,bestScore));
        if(time.getAndIncrement()%numNetworksToListenTo==numNetworksToListenTo-1) {
            System.out.println("--- REPORT ---");
            // print results and clear backlog
            Collections.sort(backlog);
            for(int i = 0; i < Math.min(backlog.size(),displayTopN); i++) {
                System.out.println("#"+(i+1)+" Best Model: "+backlog.get(i).content);
            }
            backlog.clear();
            System.out.println("--- END OF REPORT ---");
        }
    }

    class Message implements Comparable<Message> {
        double score;
        String content;
        Message(String content, double score) {
            this.score=score;
            this.content=content;
        }
        @Override
        public int compareTo(@NotNull Message o) {
            return Double.compare(score,o.score);
        }
    }
}
