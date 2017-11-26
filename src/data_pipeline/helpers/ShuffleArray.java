package data_pipeline.helpers;

import java.util.Random;

/**
 * Created by ehallmark on 11/10/17.
 */
public class ShuffleArray {
    private static Random random = new Random(System.currentTimeMillis());
    public static void shuffleArray(int[] a) {
        int n = a.length;
        random.nextInt();
        for (int i = 0; i < n; i++) {
            int change = i + random.nextInt(n - i);
            swap(a, i, change);
        }
    }

    private static void swap(int[] a, int i, int change) {
        int helper = a[i];
        a[i] = a[change];
        a[change] = helper;
    }

}
