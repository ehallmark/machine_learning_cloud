package tools;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by ehallmark on 9/6/16.
 */
public class Permutations<T> {

    public List<T[]> permute(T[] arr){
        List<T[]> list = new ArrayList<>(factorial(arr.length));
        permuteHelper(arr, 0,list);
        return list;
    }

    private int factorial(int k) {
        int i = 1;
        int toRet = 1;
        while(i<=k) {
            toRet*=i;
            i++;
        }
        return toRet;
    }

    private void permuteHelper(T[] arr, int index, List<T[]> list){
        if(index >= arr.length - 1){ //If we are at the last element - nothing left to permute
            list.add(arr.clone());
            return;
        }

        for(int i = index; i < arr.length; i++){ //For each index in the sub array arr[index...end]

            //Swap the elements at indices index and i
            T t = arr[index];
            arr[index] = arr[i];
            arr[i] = t;

            //Recurse on the sub array arr[index+1...end]
            permuteHelper(arr, index+1, list);

            //Swap the elements back
            t = arr[index];
            arr[index] = arr[i];
            arr[i] = t;
        }
    }
}
