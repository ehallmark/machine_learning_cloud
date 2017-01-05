package seeding;

import java.util.Arrays;

/**
 * Created by ehallmark on 9/6/16.
 */
public class HashCodeExample {
    public static void main(String[] args) {
        Integer[] i = new Integer[]{1,2,3};
        Integer[] same = new Integer[]{1,2,3};
        Integer[] diff = new Integer[]{1,2,4};

        String[] s = new String[]{"a","b","c"};
        String[] ssame = new String[]{"a","b","c"};
        String[] sdiff = new String[]{"a","b","c","d"};


        assert(Arrays.deepHashCode(i)==Arrays.deepHashCode(same));
        assert(Arrays.deepHashCode(i)!=Arrays.deepHashCode(diff));
        assert(Arrays.deepHashCode(s)==Arrays.deepHashCode(ssame));
        assert(Arrays.deepHashCode(s)!=Arrays.deepHashCode(sdiff));


        //for()

        System.out.println("ALL PASSED!");
    }
}
