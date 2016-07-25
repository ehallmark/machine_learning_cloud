package learning;

/**
 * Created by ehallmark on 7/25/16.
 */
public class IteratorHelper {

    public static Double[] flatten2Dto1D(Double[][] array2D) {
        if(array2D==null || array2D.length==0)return null;
        int innerLength = array2D[0].length;
        Double[] array1D = new Double[array2D.length*innerLength];
        for(int i = 0; i < array2D.length; i++) {
            for(int j = 0; j < innerLength; j++) {
                array1D[i*innerLength+j]=array2D[i][j];
            }
        }
        return array1D;
    }
}
