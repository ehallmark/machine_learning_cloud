package stocks.util;

import lombok.Getter;

import java.time.LocalDate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class Stock {
    private LocalDate date;
    @Getter
    private double[] historicalPrices;
    @Getter
    private double[] historicalVelocities;
    @Getter
    private double[] historicalReturns;
    @Getter
    private double[] historicalAccelerations;
    @Getter
    private double movingAverage5;
    @Getter
    private double movingAverage10;
    @Getter
    private double averagePrice;
    @Getter
    private double averageVelocity;
    @Getter
    private double averageAcceleration;
    @Getter
    private double price;
    @Getter
    private boolean concaveUp;
    @Getter
    private double averageReturn1;
    @Getter
    private double averageReturn5;
    @Getter
    private double averageReturn10;
    @Getter
    private final String symbol;
    @Getter
    private double variance;
    @Getter
    private double varianceNormalized;
    public Stock(String symbol, LocalDate date, double[] historicalPrices) {
        assert historicalPrices.length>=14; // at least two weeks of data
        this.date=date;
        this.symbol=symbol;
        this.historicalPrices=historicalPrices;
        init();
    }

    private void init() {
        historicalVelocities = IntStream.range(0,historicalPrices.length-1).mapToDouble(i->historicalPrices[i+1]-historicalPrices[i]).toArray();
        historicalReturns = IntStream.range(0,historicalPrices.length-1).mapToDouble(i->(historicalPrices[i+1]-historicalPrices[i])/historicalPrices[i]).toArray();
        historicalAccelerations = IntStream.range(0,historicalVelocities.length-1).mapToDouble(i->historicalVelocities[i+1]-historicalVelocities[i]).toArray();
    }

    public void nextTimeStep(double price) {
        this.price=price;
        for(int i = 0; i < historicalPrices.length-1; i++) {
            historicalPrices[i]=historicalPrices[i+1];
            if(i<historicalVelocities.length-1) {
                historicalVelocities[i]=historicalVelocities[i+1];
            }
            if(i<historicalReturns.length-1) {
                historicalReturns[i]=historicalReturns[i+1];
            }
            if(i<historicalAccelerations.length-1) {
                historicalAccelerations[i]=historicalAccelerations[i+1];
            }
        }
        historicalPrices[historicalPrices.length-1]=this.price;
        historicalVelocities[historicalVelocities.length-1] = historicalPrices[historicalPrices.length-1]-historicalPrices[historicalPrices.length-2];
        historicalReturns[historicalReturns.length-1] = (historicalPrices[historicalPrices.length-1]-historicalPrices[historicalPrices.length-2])/historicalPrices[historicalPrices.length-2];
        historicalAccelerations[historicalAccelerations.length-1] = historicalVelocities[historicalVelocities.length-1]-historicalVelocities[historicalVelocities.length-2];
        averagePrice = DoubleStream.of(historicalPrices).sum()/historicalPrices.length;
        variance = IntStream.range(0,historicalPrices.length).mapToDouble(i->Math.pow(historicalPrices[i]-averagePrice,2d)).sum()/(historicalPrices.length-1);
        varianceNormalized = variance/averagePrice;
        averageVelocity = DoubleStream.of(historicalVelocities).sum()/historicalVelocities.length;
        averageAcceleration = DoubleStream.of(historicalAccelerations).sum()/historicalAccelerations.length;
        movingAverage5 = IntStream.range(historicalPrices.length-5,historicalPrices.length).mapToDouble(i->historicalPrices[i]).sum()/5;
        movingAverage10 = IntStream.range(historicalPrices.length-10,historicalPrices.length).mapToDouble(i->historicalPrices[i]).sum()/10;
        concaveUp = historicalAccelerations[historicalAccelerations.length-1]>0;
        averageReturn1 = historicalReturns[historicalReturns.length-1];
        averageReturn5 = IntStream.range(historicalReturns.length-5,historicalReturns.length).mapToDouble(i->historicalReturns[i]).sum()/5;
        averageReturn10 = IntStream.range(historicalReturns.length-10,historicalReturns.length).mapToDouble(i->historicalReturns[i]).sum()/10;
    }



    @Override
    public String toString() {
        return "Stock: "+symbol
                +"\n\tPrice: "+price
                +"\n\tAverage Price: "+averagePrice
                +"\n\tVariance: "+variance
                +"\n\tVariance (normalized): "+varianceNormalized
                +"\n\tAverage Velocity: "+averageVelocity
                +"\n\tAverage Accel: "+averageAcceleration
                +"\n\tConcave up: "+concaveUp
                +"\n\tAverage return 1: "+averageReturn1
                +"\n\tAverage return 5: "+averageReturn5
                +"\n\tAverage return 10: "+averageReturn10
                +"\n\tMoving average 5: "+movingAverage5
                +"\n\tMoving average 10: "+movingAverage10;
    }


}
