package data_pipeline.models.listeners;

import data_pipeline.helpers.Function3;
import data_pipeline.models.exceptions.StoppingConditionMetException;
import data_pipeline.optimize.nn_optimization.ModelWrapper;
import org.deeplearning4j.nn.api.Model;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * Created by Evan on 10/29/2017.
 */
public class OptimizationScoreListener<T extends Model> implements IterationListener {
    private static Map<Model,Double> modelToBestScoreMap = Collections.synchronizedMap(new HashMap<>());
    private boolean invoked = false;
    private Double previousAverageError;
    private double averageError;
    private int iterationCount = 0;
    private Double smallestAverage;
    private Integer smallestAverageEpoch;
    private Double startingAverageError;
    private int printIterations;
    // train
    private List<Double> movingAverage = new ArrayList<>();
    private final int averagePeriod = 10;
    private Function3<T,LocalDateTime,Double,Void> saveFunction;
    private  Function<T,Double> testErrorFunction;
    private Long lastTime;
    private ModelWrapper<T> net;
    private final MultiScoreReporter reporter;
    public OptimizationScoreListener(MultiScoreReporter reporter, ModelWrapper<T> net, int printIterations, Function<T,Double> testErrorFunction, Function3<T,LocalDateTime,Double,Void> saveFunction) {
        this.printIterations = printIterations;
        this.testErrorFunction=testErrorFunction;
        this.saveFunction=saveFunction;
        this.net=net;
        this.reporter=reporter;
        modelToBestScoreMap.put(net.getNet(),Double.MAX_VALUE);
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
        if (iterationCount % printIterations == printIterations-1) {
            long newTime = System.currentTimeMillis();
           // System.out.println("Time to complete: "+((newTime-lastTime)/1000)+" seconds");
            lastTime = newTime;

            double error = testErrorFunction.apply(net.getNet());
            StringJoiner message = new StringJoiner("\n");
            message.add("Results for: " + net.describeHyperParameters());
            message.add("  Model Score: " + model.score() + ", Test Error: " + error);
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
                message.add("  Sampling Test Error (Iteration "+iterationCount+"): "+error);
                message.add("  Original Average Error: " + startingAverageError);
                message.add("  Smallest Average Error (Iteration "+smallestAverageEpoch+"): " + smallestAverage);
                message.add("  Current Average Error: " + averageError);
                while(movingAverage.size()>averagePeriod/2) {
                    movingAverage.remove(0);
                }
            }
            synchronized (reporter) {
                reporter.addToCurrentReport(message.toString(),smallestAverage==null?error:smallestAverage);
            }
        }
        if(previousAverageError!=null&&smallestAverage!=null&&smallestAverageEpoch!=null) {
            // check conditions for saving model
            // only save if it's the best model
            if(smallestAverageEpoch.equals(iterationCount)) {
                double currentBestModelError = modelToBestScoreMap.values().stream().mapToDouble(d->d).min().orElse(Double.MAX_VALUE);
                modelToBestScoreMap.put(net.getNet(),averageError);

                if(currentBestModelError > averageError) {
                    System.out.println("Saving model: "+net.describeHyperParameters());
                    try {
                        saveFunction.apply(net.getNet(), LocalDateTime.now(), averageError);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if(averageError>previousAverageError) {
                // check stopping conditions
                if (averageError > smallestAverage * 1.2) {
                    //System.out.println("Stopping condition met!!!");
                    //throw new StoppingConditionMetException();
                }
            }
        }
        previousAverageError = averageError;
    }
}
