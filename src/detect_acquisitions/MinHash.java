package detect_acquisitions;

import java.util.*;
import java.util.function.Function;

public class MinHash {
    private static final int SHINGLE_SIZE = 4;
    private static List<Function<Object,Long>> hashFunctions;
    private static final int NUM_HASH_FUNCTIONS = 1000;
    static {
        hashFunctions = new ArrayList<>(NUM_HASH_FUNCTIONS);
        Random random = new Random(2352);
        for(int i = 0; i < NUM_HASH_FUNCTIONS; i++) {
            final int r = random.nextInt();
            final int idx = i;
            hashFunctions.add((obj->((long)obj.hashCode())+(String.valueOf(idx)+obj.toString()+r).hashCode()));
        }
    }


    public static boolean fuzzyContains(String largeText, String toContain) {
        double threshold = 3d/NUM_HASH_FUNCTIONS;
        long[] shingles1 = hashShingles(createShinglesFromText(largeText));
        long[] shingles2 = hashShingles(createShinglesFromText(toContain));
        double v = 0d;
        for(int i = 0; i < shingles1.length; i++) {
            if(shingles1[i]==shingles2[i]) {
                v++;
            }
        }
        System.out.println("V: "+v/shingles1.length);
        return v/shingles1.length > threshold;
    }

    public static Set<String> createShinglesFromText(String text) {
        Set<String> shingles = new HashSet<>();
        for(int i = 0; i < text.length()-SHINGLE_SIZE+1; i++) {
            shingles.add(text.substring(i,i+SHINGLE_SIZE));
        }
        return shingles;
    }

    public static long[] hashShingles(Set<String> shingles) {
        return hashFunctions.stream().mapToLong(function->{
            return shingles.stream().mapToLong(shingle->function.apply(shingle)).min().orElse(Long.MAX_VALUE);
        }).toArray();
    }

    public static void main(String[] args) {
        System.out.println(fuzzyContains("Microsoft to acquire LinkedIn | Stories\n" +
                "news.microsoft.com/.../microsoft-to-acquire-linkedin\n" +
                "Jun 12, 2016 · “Just as we have changed the way the world connects to opportunity, this relationship with Microsoft, and the combination of their cloud and LinkedIn’s ...","microsoft"));
        System.out.println(fuzzyContains("Microsoft to acquire LinkedIn | Stories\n" +
                "news.microsoft.com/.../microsoft-to-acquire-linkedin\n" +
                "Jun 12, 2016 · “Just as we have changed the way the world connects to opportunity, this relationship with Microsoft, and the combination of their cloud and LinkedIn’s ...","linkedin"));
        System.out.println(fuzzyContains("Microsoft to acquire LinkedIn | Stories\n" +
                "news.microsoft.com/.../microsoft-to-acquire-linkedin\n" +
                "Jun 12, 2016 · “Just as we have changed the way the world connects to opportunity, this relationship with Microsoft, and the combination of their cloud and LinkedIn’s ...","panasonic"));

    }
}
