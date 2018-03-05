package graphical_modeling.util;

import java.util.Arrays;

/**
 * Created by ehallmark on 4/26/17.
 */
public class MathHelper {

    public static double euclideanDistance(double[] x, double[] y) {
        double[] diff = sub(x,y);
        double[] squares = mul(diff,diff);
        double sum = Arrays.stream(squares).sum();
        return Math.sqrt(sum);
    }

    public static int indexOfMaxValue(double[] x) {
        if(x.length==0) throw new RuntimeException("Illegal argument length");
        int idx = 0;
        double max = x[0];
        for(int i = 1; i < x.length; i++) {
            if(max<x[i]) {
                idx=i;
                max=x[i];
            }
        }
        return idx;
    }


    public static double[] sub(double[] x, double[] y) {
        double[] newX = Arrays.copyOf(x,x.length);
        for(int i = 0; i < Math.min(x.length,y.length); i++) {
            newX[i]-=y[i];
        }
        return newX;
    }

    public static double[] add(double[] x, double[] y) {
        double[] newX = Arrays.copyOf(x,x.length);
        for(int i = 0; i < Math.min(x.length,y.length); i++) {
            newX[i]+=y[i];
        }
        return newX;
    }

    public static double[] mul(double[] x, double[] y) {
        double[] newX = Arrays.copyOf(x,x.length);
        for(int i = 0; i < Math.min(x.length,y.length); i++) {
            newX[i]*=y[i];
        }
        return newX;
    }

    public static double[] div(double[] x, double d) {
        return mul(x,1d/d);
    }

    public static double[] add(double[] x, double d) {
        double[] newX = Arrays.copyOf(x,x.length);
        for(int i = 0; i < x.length; i++) {
            newX[i]+=d;
        }
        return newX;
    }

    public static double[] sub(double[] x, double d) {
        return add(x,-d);
    }

    public static double[] mul(double[] x, double d) {
        double[] newX = Arrays.copyOf(x,x.length);
        for(int i = 0; i < x.length; i++) {
            newX[i]*=d;
        }
        return newX;
    }

}
