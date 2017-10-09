package assignee_normalization;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import info.debatty.java.stringsimilarity.interfaces.StringDistance;
import models.keyphrase_prediction.KeywordModelRunner;
import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.scorers.TechnologyScorer;
import models.keyphrase_prediction.stages.Stage;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import seeding.Constants;
import seeding.Database;
import tools.OpenMapBigRealMatrix;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/8/2017.
 */
public class NormalizeAssignees {
    private static Map<String,Pair<String,Double>> rawToNormalizedAssigneeNameMapWithScores;
    private static Map<String,String> rawToNormalizedAssigneeNameMap;
    private static final File rawToNormalizedAssigneeNameFile = new File(Constants.DATA_FOLDER+"raw_to_normalized_assignee_name_map.jobj");

    public static void save() {
        rawToNormalizedAssigneeNameMap = rawToNormalizedAssigneeNameMapWithScores.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue()._1));
        Database.trySaveObject(rawToNormalizedAssigneeNameMap,rawToNormalizedAssigneeNameFile);
        // save to csv
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File("data/raw_to_normalized_map.csv")));
            writer.write("Original Name, Normalized Name\n");
            rawToNormalizedAssigneeNameMap.forEach((raw, norm) -> {
                try {
                    writer.write("\"" + raw + "\",\"" + norm + "\"\n");
                } catch(Exception e) {
                    e.printStackTrace();
                }
            });
            writer.flush();
            writer.close();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String,String> getRawToNormalizedAssigneeNameMap() {
        if(rawToNormalizedAssigneeNameMap==null) {
            rawToNormalizedAssigneeNameMap = (Map<String,String>) Database.tryLoadObject(rawToNormalizedAssigneeNameFile);
        }
        return rawToNormalizedAssigneeNameMap;
    }

    private static boolean badPrefix(String s1, String s2) {
        return !s1.contains(" ")&&s1.length()<s2.length()&&!s2.startsWith(s1+" ");
    }
    private static void prefixSearchHelper(Collection<String> assignees, RadixTree<String> prefixTrie, StringDistance distanceFunction, Map<String,Integer> portfolioSizeMap) {
        assignees.parallelStream().forEach(assignee->{
            String lookup = assignee.contains(" ") ? assignee : (assignee+" ");
            prefixTrie.getValuesForClosestKeys(lookup).forEach(val->{
                if(!val.equals(assignee)) {
                    if(badPrefix(assignee,val)||badPrefix(val,assignee)) {
                        return; // special case
                    }
                    if(assignees.contains(val)) {
                        // if other assignee has larger size, return
                        int size1 = portfolioSizeMap.get(val);
                        int size2 = portfolioSizeMap.get(assignee);
                        if(size2 > size1) {
                            return;
                        }
                    }
                    double distance = distanceFunction.distance(assignee, val);
                    Pair<String, Double> previousNormalization = rawToNormalizedAssigneeNameMapWithScores.get(val);
                    if (previousNormalization == null || previousNormalization._2 > distance) {
                        rawToNormalizedAssigneeNameMapWithScores.put(val, new Pair<>(assignee, distance));
                        //System.out.println(val + " => " + assignee + ": " + distance);
                    }
                }
            });
        });
    }

    public static void main(String[] args) {
        Levenshtein levenshtein = new Levenshtein();
        rawToNormalizedAssigneeNameMapWithScores = Collections.synchronizedMap(new HashMap<>());

        List<String> allAssignees = Collections.synchronizedList(new ArrayList<>(Database.getAssignees()));

        Map<String,Integer> assigneeToPortfolioSizeMap = allAssignees.parallelStream().collect(Collectors.toMap(a->a,a->{
            return Database.getAssetCountFor(a);
        }));

        int minPortfolioSize = 50;
        Collection<String> largestAssignees = new HashSet<>(assigneeToPortfolioSizeMap.entrySet().parallelStream().filter(e->e.getValue()>=minPortfolioSize)
                .map(e->e.getKey()).collect(Collectors.toList()));
        System.out.println("Num large assignees: "+largestAssignees.size());

        // first merge any mistakes in the large assignees
        {
            RadixTree<String> radix = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
            largestAssignees.forEach(assignee->radix.put(assignee,assignee));
            prefixSearchHelper(largestAssignees, radix, levenshtein, assigneeToPortfolioSizeMap);
            // check if any large assignees have been normalized
            Collection<String> updatedFromLargeAssignees = largestAssignees.parallelStream().filter(assignee->rawToNormalizedAssigneeNameMapWithScores.containsKey(assignee)).collect(Collectors.toList());
            largestAssignees.removeAll(updatedFromLargeAssignees);
            System.out.println("Num Updated Large assignees: "+updatedFromLargeAssignees.size());
        }


        RadixTree<String> assigneePrefixTrie = Database.getAssigneePrefixTrie();
        prefixSearchHelper(largestAssignees, assigneePrefixTrie, levenshtein, assigneeToPortfolioSizeMap);

        System.out.println("Total normalizations after prefix search: "+rawToNormalizedAssigneeNameMapWithScores.size() + " / "+allAssignees.size());

        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger changes = new AtomicInteger(0);
        allAssignees.parallelStream().filter(a->!largestAssignees.contains(a)).forEach(rawAssignee->{
            Pair<String,Double> mostSimilarCandidate = largestAssignees.stream().map(candidate->{
                return new Pair<>(candidate,levenshtein.distance(candidate,rawAssignee));
            }).reduce((p1,p2)->p1._2<p2._2 ? p1 : p2).get();
            double maxDistance = 0.05 * (rawAssignee.length()+mostSimilarCandidate._1.length());
            if(mostSimilarCandidate._2<maxDistance) {
                Pair<String,Double> previousNormalization = rawToNormalizedAssigneeNameMapWithScores.get(rawAssignee);
                if(previousNormalization==null || previousNormalization._2 > mostSimilarCandidate._2) {
                    //System.out.println(rawAssignee + " => " + mostSimilarCandidate._1 + ": " + mostSimilarCandidate._2);
                    rawToNormalizedAssigneeNameMapWithScores.put(rawAssignee,mostSimilarCandidate);
                    changes.getAndIncrement();
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished assignee: "+changes.get()+" / "+cnt.get());
            }

        });

        System.out.println("Total normalizations after distance search: "+rawToNormalizedAssigneeNameMap.size() + " / "+allAssignees.size());

        save();

        /*
        int MAX_DOC_FREQUENCY = 70000;

        // sum of word counts after first word
        Map<String,Integer> wordCountMap = allAssignees.parallelStream().flatMap(assignee->{
            String[] words = assignee.split("\\s+");
            if(words.length<=1) return Stream.empty();
            Set<String> seen = new HashSet<>();
            return IntStream.range(1,words.length).mapToObj(i->{
                String word = words[i];
                if (!seen.contains(word)) {
                    seen.add(word);
                    return new Pair<>(word,i*i);
                }
                return null;
            }).filter(p->p!=null);
        }).collect(Collectors.groupingBy(pair->pair._1,Collectors.summingInt(pair->pair._2)));

        {
            List<Pair<String, Integer>> wordCountList = wordCountMap.entrySet().parallelStream().sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue())).map(e -> new Pair<>(e.getKey(), e.getValue())).collect(Collectors.toList());

            wordCountList.stream().limit(100).forEach(pair -> {
                System.out.println("word " + pair._1 + ": " + pair._2);
            });

            Set<String> stopWords = new HashSet<>();
            stopWords.add("OF");
            stopWords.add("THE");
            stopWords.add("AND");

            AtomicInteger numChanges = new AtomicInteger(0);
            Map<String, String> originalToNormalizedAssigneeMap = allAssignees.parallelStream().collect(Collectors.toMap(k -> k, assignee -> {
                String[] words = assignee.split("\\s+");
                StringJoiner sj = new StringJoiner(" ");
                int i;
                for (i = 0; i < (words.length + 1) / 2; i++) {
                    sj.add(words[i]);
                }
                for (i = i; i < words.length; i++) {
                    String word = words[i];
                    double maxScore = ((double) MAX_DOC_FREQUENCY);
                    double wordScore = Math.log(Math.E + i) * wordCountMap.get(word) / Math.log(Math.E + words.length);
                    //System.out.println("score for word: "+word+" = "+wordScore);
                    if (stopWords.contains(word) || maxScore > wordScore) {
                        sj.add(word);
                    } else {
                        break;
                    }
                }
                String normalized = sj.toString();
                if (!assignee.equals(normalized)) {
                    System.out.println(numChanges.getAndIncrement());
                    System.out.println(assignee + ": " + normalized);
                }
                return normalized;
            }));

            System.out.println("Total num changes: " + numChanges.get());
        } */
    }
}
