package models.similarity_models.signatures.scorers;

import models.similarity_models.signatures.StoppingConditionMetException;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.optimize.api.IterationListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Created by Evan on 10/29/2017.
 */
public class DefaultScoreListener implements IterationListener {
    private boolean invoked = false;
    private Double previousAverageError;
    private double averageError;
    private int iterationCount = 0;
    private Double smallestAverage;
    private Integer smallestAverageEpoch;
    private Double startingAverageError;
    private int printIterations;
    private AtomicBoolean isSavedFlag;
    // train
    private List<Double> movingAverage = new ArrayList<>();
    private final int averagePeriod = 10;
    private Function<Void,Void> saveFunction;
    private AtomicBoolean stoppingConditionFlag;
    private  Function<Void,Double> testFunction;
    private Long lastTime;
    public DefaultScoreListener(int printIterations, Function<Void,Double> testFunction, Function<Void,Void> saveFunction, AtomicBoolean isSavedFlag, AtomicBoolean stoppingConditionFlag) {
        this.printIterations = printIterations;
        this.isSavedFlag=isSavedFlag;
        this.testFunction=testFunction;
        this.saveFunction=saveFunction;
        this.stoppingConditionFlag=stoppingConditionFlag;
    }

    @Override
    public boolean invoked() {
        return invoked;
    }

    @Override
    public void invoke() {
        invoked = true;
    }
    @Override
    public void iterationDone(Model model, int iteration) {
        if(lastTime==null) {
            lastTime = System.currentTimeMillis();
        }
        if(iterationCount % (printIterations/10) == (printIterations/10)-1) {
            System.out.print("-");
        }
        iterationCount++;
        if(iterationCount%10000==9999&&!isSavedFlag.get()) {
            try {
                saveFunction.apply(null);
                isSavedFlag.set(false);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }
        if (iterationCount % printIterations == printIterations-1) {
            long newTime = System.currentTimeMillis();
            System.out.println("Time to complete: "+((newTime-lastTime)/1000)+" seconds");
            lastTime = newTime;
            System.out.print("Testing...");
            double error = testFunction.apply(null);
            System.out.println(" Error: "+error);
            movingAverage.add(error);
            if(movingAverage.size()==averagePeriod) {
                averageError = movingAverage.stream().mapToDouble((d -> d)).average().getAsDouble();
                if(startingAverageError==null) {
                    startingAverageError = averageError;
                }
                if(smallestAverage==null||smallestAverage>averageError) {
                    smallestAverage = averageError;
                    smallestAverageEpoch=iterationCount;
                }
                System.out.println("Sampling Test Error (Iteration "+iterationCount+"): "+error);
                System.out.println("Original Average Error: " + startingAverageError);
                System.out.println("Smallest Average Error (Iteration "+smallestAverageEpoch+"): " + smallestAverage);
                System.out.println("Current Average Error: " + averageError);
                while(movingAverage.size()>averagePeriod/2) {
                    movingAverage.remove(0);
                }
            }
        }
        if(previousAverageError!=null&&smallestAverage!=null) {
            // check conditions for saving model
            if(averageError>previousAverageError && previousAverageError.equals(smallestAverage)) {
                System.out.println("Saving model...");
                try {
                    saveFunction.apply(null);
                } catch(Exception e) {
                    System.out.println("Error while saving: "+e.getMessage());
                    e.printStackTrace();
                }
                // check stopping conditions
                if (averageError > smallestAverage * 1.2) {
                    stoppingConditionFlag.set(true);
                    System.out.println("Stopping condition met!!!");
                    throw new StoppingConditionMetException();
                }
            }
        }
        previousAverageError = averageError;
    }
}
