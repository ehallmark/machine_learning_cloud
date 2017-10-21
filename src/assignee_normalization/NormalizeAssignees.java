package assignee_normalization;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.Levenshtein;
import info.debatty.java.stringsimilarity.NormalizedLevenshtein;
import info.debatty.java.stringsimilarity.QGram;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/8/2017.
 */
public class NormalizeAssignees {
   // private static Map<String,Pair<String,Double>> rawToNormalizedAssigneeNameMapWithScores;
    private static final File rawToNormalizedAssigneeNameFile = new File(Constants.DATA_FOLDER+"raw_to_normalized_assignee_name_map.jobj");
    private static final File portfolioSizeMapFile = new File(Constants.DATA_FOLDER+"normalized_portfolio_size_map.jobj");

    private static final int MIN_ASSIGNEE_LENGTH = 3;
    public static String manualMerge(String rawAssignee) {
        switch(rawAssignee) {
            case "SK HYNIX": {
                return "HYNIX";
            }
            case "MATSUSHITA ELECTRIC INDUSTRIAL": {
                return "PANASONIC";
            }
            case "YAMAHA HATSUDOKI": {
                return "YAMAHA";
            }
            case "YAMAHA MOTOR": {
                return "YAMAHA";
            }
            case "TOYOTA JIDOSHA": {
                return "TOYOTA";
            }
            case "SONY COMPUTER ENTERTAINMENT": {
                return "SONY INTERACTIVE ENTERTAINMENT";
            }
            default: {
                return rawAssignee;
            }
        }
    }

    static Collection<String> manualBadPrefixes = Arrays.asList(
            "KABUSHIKI KAISHA ",
            "SK ",
            "THE "
    );

    static Collection<String> manualBadSuffixes = Arrays.asList(
            " KABUSHIKI KAISHA",
            " LP",
            " CORPORATION"
    );

    private Map<String,String> rawToNormalizedMap;
    private Map<String,Integer> portfolioSizeMap;
    public NormalizeAssignees(Map<String,String> rawToNormalizedMap, Map<String,Integer> portfolioSizeMap) {
        this.rawToNormalizedMap=rawToNormalizedMap;
        this.portfolioSizeMap=portfolioSizeMap;
    }
    public NormalizeAssignees() {
    }

    public String normalizedAssignee(String assignee) {
        if(rawToNormalizedMap==null) {
            synchronized (this) {
                if(rawToNormalizedMap==null) {
                    rawToNormalizedMap = getRawToNormalizedAssigneeNameMap();
                }
            }
        }
        if(portfolioSizeMap==null) {
            synchronized (this) {
                if(portfolioSizeMap==null) {
                    portfolioSizeMap = (Map<String, Integer>) Database.tryLoadObject(portfolioSizeMapFile);
                }
            }
        }

        Set<String> seen = new HashSet<>();
        seen.add(assignee);
        boolean keepGoing = true;
        while(keepGoing && rawToNormalizedMap.containsKey(assignee)) {
            assignee = rawToNormalizedMap.get(assignee);
            if(seen.contains(assignee)) {
                keepGoing = false;
            }
            seen.add(assignee);
        }
        assignee = seen.stream().sorted((a1,a2)->Integer.compare(portfolioSizeMap.getOrDefault(a2,0),portfolioSizeMap.getOrDefault(a1,0))).findFirst().get();
        return manualCleanse(assignee);
    }

    public static String manualCleanse(String cleanIsh) {
        if(cleanIsh.length() > MIN_ASSIGNEE_LENGTH && cleanIsh.contains(" ")) {
            // clean prefixes
            boolean prefixProblem = true;
            while(prefixProblem) {
                prefixProblem = false;
                for (String pref : manualBadPrefixes) {
                    if (cleanIsh.startsWith(pref) && cleanIsh.length() > pref.length() + MIN_ASSIGNEE_LENGTH) {
                        cleanIsh = cleanIsh.substring(pref.length());
                        prefixProblem = true;
                    }
                }
            }
            // clean suffixes
            boolean suffixProblem = true;
            while(suffixProblem) {
                suffixProblem = false;
                for (String suff : manualBadSuffixes) {
                    if (cleanIsh.endsWith(suff) && cleanIsh.length() > suff.length() + MIN_ASSIGNEE_LENGTH) {
                        cleanIsh = cleanIsh.substring(0, cleanIsh.length() - suff.length());
                        suffixProblem = true;
                    }
                }
                String[] words = cleanIsh.split(" ");
                if(words.length > 1 && cleanIsh.length() - words[words.length-1].length() > MIN_ASSIGNEE_LENGTH && words[words.length-1].length() <= 2) {
                    cleanIsh = String.join(" ",Arrays.copyOf(words,words.length-1));
                    suffixProblem = true;
                }
            }
            // check for manual changes
            cleanIsh = manualMerge(cleanIsh);
        }
        return cleanIsh;
    }

    public static void saveAs(String name, Map<String,Pair<String,Double>> rawToNormalizedMap,Map<String,Integer> portfolioSizeMap) {
        System.out.println("Starting to save: "+name);

        Map<String,String> rawToNormalizedAssigneeNameMap = rawToNormalizedMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue()._1));
        Database.trySaveObject(rawToNormalizedAssigneeNameMap,new File(name));
        // save portoflio size map
        Database.trySaveObject(portfolioSizeMap,portfolioSizeMapFile);
        // save to csv
        NormalizeAssignees normalizer = new NormalizeAssignees(rawToNormalizedAssigneeNameMap,portfolioSizeMap);
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(name+".csv")));
            writer.write("Original Name, Normalized Name\n");
            new TreeSet<>(rawToNormalizedAssigneeNameMap.keySet()).forEach(raw -> {
                try {
                    writer.write("\"" + raw + "\",\"" + normalizer.normalizedAssignee(raw) + "\"\n");
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

    public static void save(Map<String,String> rawToNormalizedMap, Map<String,Integer> portfolioSizeMap) {
        saveAs(rawToNormalizedAssigneeNameFile.getAbsolutePath(), rawToNormalizedMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(),e->new Pair<>(e.getValue(),0d))),portfolioSizeMap);
    }

    public static synchronized Map<String,String> getRawToNormalizedAssigneeNameMap() {
        return (Map<String,String>) Database.tryLoadObject(rawToNormalizedAssigneeNameFile);
    }

    private static boolean badPrefix(String s1, String s2) {
        return !s1.contains(" ")&&s1.length()<s2.length()&&!s2.startsWith(s1+" ");
    }
    private static void prefixSearchHelper(Collection<String> assignees, RadixTree<String> prefixTrie, StringDistance distanceFunction, Map<String,Pair<String,Double>> rawToNormalizedMap, Map<String,Integer> portfolioSizeMap) {
        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger changes = new AtomicInteger(0);
        assignees.parallelStream().forEach(assignee->{
            String lookup = assignee.contains(" ") ? assignee : (assignee+" ");
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished prefix search: "+changes.get()+" / "+cnt.get());
            }
            AtomicBoolean changed = new AtomicBoolean(false);
            prefixTrie.getValuesForClosestKeys(lookup).forEach(val->{
                if(!val.equals(assignee)) {
                    if(badPrefix(assignee,val)||badPrefix(val,assignee)) {
                        return; // special case
                    }
                    if(Math.abs(assignee.length()-val.length()) > Math.min(assignee.length(),val.length())) {
                        return; // too much change
                    }

                    if(assignees.contains(val)) {
                        // if other assignee has larger size, return
                        int size1 = portfolioSizeMap.getOrDefault(val,0);
                        int size2 = portfolioSizeMap.getOrDefault(assignee,0);
                        if(size2 > size1) {
                            return;
                        }
                    }
                    double distance = distanceFunction.distance(assignee, val) / Math.log(Math.E+portfolioSizeMap.get(assignee));
                    Pair<String, Double> previousNormalization = rawToNormalizedMap.get(val);
                    if (previousNormalization == null || previousNormalization._2 > distance) {
                        rawToNormalizedMap.put(val, new Pair<>(assignee, distance));
                        changed.set(true);
                        //System.out.println(val + " => " + assignee + ": " + distance);
                    }
                }
            });
            if(changed.get()) changes.getAndIncrement();
        });
    }

    private static void mergeAssignees(Collection<String> allAssignees, Collection<String> largeAssignees, Map<String,Pair<String,Double>> rawToNormalizedMap, Map<String,Integer> assigneeToPortfolioSizeMap, StringDistance distance) {
        RadixTree<String> radix = new ConcurrentRadixTree<>(new DefaultByteArrayNodeFactory());
        allAssignees.forEach(assignee->radix.put(assignee,assignee));
        prefixSearchHelper(largeAssignees, radix, distance, rawToNormalizedMap, assigneeToPortfolioSizeMap);
        // check if any large assignees have been normalized
        Collection<String> updatedFromLargeAssignees = allAssignees.parallelStream().filter(assignee->rawToNormalizedMap.containsKey(assignee)).collect(Collectors.toList());
        allAssignees.removeAll(updatedFromLargeAssignees);
        largeAssignees.removeAll(updatedFromLargeAssignees);
        System.out.println("Num Large Assignees Merged: "+updatedFromLargeAssignees.size());
    }

    private static Set<String> buildBadSuffixes(Collection<String> allAssignees) {
        Map<String,Long> suffixes = allAssignees.parallelStream().map(assignee->{
            String[] words = assignee.split("\\s+");
            if(words.length < 3) return null;
            return words[words.length-1];
        }).filter(word->word!=null).collect(Collectors.groupingBy(w->w,Collectors.counting()));

        double minSuffixScore = 10d;//allAssignees.size() * 0.01;
        List<Pair<String,Double>> suffixList = suffixes.entrySet().parallelStream()
                .map(e->new Pair<>(e.getKey(),e.getValue()/Math.pow(e.getKey().length(),3)))
                .filter(p->p._2>minSuffixScore).sorted((e1,e2)->e2._2.compareTo(e1._2))
                .collect(Collectors.toList());
        suffixList.forEach(p->{
            System.out.println(p._1+": "+p._2);
        });
        System.out.println("Total num bad suffixes: "+suffixList.size());
        return suffixList.stream().map(p->p._1).collect(Collectors.toSet());
    }

    private static Collection<String> runIteration(Collection<String> allAssignees, Map<String,Pair<String,Double>> rawToNormalizedMap, Map<String,Integer> assigneeToPortfolioSizeMap, StringDistance distanceFunction, int epoch) {
        Set<String> badSuffixes = buildBadSuffixes(allAssignees);

        allAssignees = allAssignees.parallelStream().map(assignee->{
            if(assignee.length()<=1) return null;

            String newWord = manualCleanse(assignee);

            // get assignee words
            String[] words = newWord.split("\\s+");

            // get rid of lone characters at the end
            while(String.join(" ",words).length() >= 7 && words.length > MIN_ASSIGNEE_LENGTH) {
                if(words[words.length-1].length()<=3) {
                    words = Arrays.copyOf(words,words.length-1);
                } else {
                    break;
                }
            }

            while(words.length >= MIN_ASSIGNEE_LENGTH && String.join(" ", Arrays.copyOf(words,words.length-1)).length() > MIN_ASSIGNEE_LENGTH+2 && badSuffixes.contains(words[words.length-1])) {
                words = Arrays.copyOf(words,words.length-1);
            }
            newWord = String.join(" ",words);

            // do manual cleans
            newWord = manualCleanse(newWord);

            int portfolioSize = assigneeToPortfolioSizeMap.get(assignee);
            int newSize = assigneeToPortfolioSizeMap.getOrDefault(newWord, 0);
            assigneeToPortfolioSizeMap.put(newWord, Math.max(portfolioSize,newSize));
            return newWord;

        }).filter(a->a!=null).distinct().collect(Collectors.toList());

        // check assignees
        allAssignees.parallelStream().forEach(a->{
            if(a.length()==1) {
                throw new RuntimeException("BAD ASSIGNEE: "+a);
            }
        });

        int minPortfolioSize = 10;
        Collection<String> largestAssignees = new HashSet<>(allAssignees.parallelStream().map(a->new Pair<>(a,assigneeToPortfolioSizeMap.getOrDefault(a,0)))
                .filter(e->e._2>minPortfolioSize)
                .sorted((e1,e2)->e2._2.compareTo(e1._2))
                .map(e->e._1).collect(Collectors.toList()));

        System.out.println("Num large assignees: "+largestAssignees.size());

        mergeAssignees(allAssignees, largestAssignees, rawToNormalizedMap, assigneeToPortfolioSizeMap, distanceFunction);

        int maxNumAssigneeSamples = 50;
        Random rand = new Random();
        Collection<String> largestAssigneeSamples = new HashSet<>(assigneeToPortfolioSizeMap.entrySet().parallelStream()
                .filter(e->e.getValue()>minPortfolioSize)
                .filter(e->rand.nextDouble()>(0.5*minPortfolioSize/e.getValue()))
                .sorted((e1,e2)->e2.getValue().compareTo(e1.getValue()))
                .limit(maxNumAssigneeSamples)
                .map(e->e.getKey()).collect(Collectors.toList()));

        System.out.println("Total normalizations after prefix search: "+rawToNormalizedMap.size() + " / "+allAssignees.size());
        saveAs("test 0-"+epoch, rawToNormalizedMap, assigneeToPortfolioSizeMap);

        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger changes = new AtomicInteger(0);
        Collection<String> changedAssignees = new HashSet<>();
        allAssignees.parallelStream().filter(a->!largestAssigneeSamples.contains(a)).forEach(rawAssignee->{
            Pair<String,Double> mostSimilarCandidate = largestAssigneeSamples.stream().map(candidate->{
                return new Pair<>(candidate,distanceFunction.distance(candidate,rawAssignee));
            }).reduce((p1,p2)->p1._2<p2._2 ? p1 : p2).get();
            double maxDistance = Math.min(0.2,0.02 * Math.log(Math.E+Math.max(rawAssignee.split(" ").length, mostSimilarCandidate._1.split(" ").length)));
            // make sure that if begins with abbreviation, they are the same
            // do this by making sure a high degree of similarity in the first word
            String otherFirstWord = mostSimilarCandidate._1.split(" ")[0];
            String firstWord = rawAssignee.split(" ")[0];
            if(firstWord.length() > 5 || otherFirstWord.equals(firstWord)) {
                if (mostSimilarCandidate._2 < maxDistance) {
                    Pair<String, Double> previousNormalization = rawToNormalizedMap.get(rawAssignee);
                    if (previousNormalization == null || previousNormalization._2 > mostSimilarCandidate._2) {
                        //System.out.println(rawAssignee + " => " + mostSimilarCandidate._1 + ": " + mostSimilarCandidate._2);
                        rawToNormalizedMap.put(rawAssignee, mostSimilarCandidate);
                        changedAssignees.add(rawAssignee);
                        changes.getAndIncrement();
                    }
                }
            }
            if(cnt.getAndIncrement()%10000==9999) {
                System.out.println("Finished assignee: "+changes.get()+" / "+cnt.get());
            }
        });
        allAssignees.removeAll(changedAssignees);
        System.out.println("CHANGED ASSIGNEES: ");
        changedAssignees.stream().sorted().forEach(a->{
            System.out.println(a+" => "+ rawToNormalizedMap.get(a));
        });

        saveAs("test 1-"+epoch,rawToNormalizedMap, assigneeToPortfolioSizeMap);

        System.out.println("Total normalizations after distance search: "+rawToNormalizedMap.size() + " / "+allAssignees.size());

        return allAssignees;
    }

    public static void main(String[] args) {
        // find most common suffixes
        Collection<String> allAssignees = Collections.synchronizedList(new ArrayList<>(Database.getAssignees()));

        Map<String,Integer> assigneeToPortfolioSizeMap = new HashMap<>(allAssignees
                .parallelStream()
                .collect(Collectors.toMap(a->a,a->Database.getAssetCountFor(a))));

        int numEpochs = 10;
        Map<String,String> rawToNormalizedAssigneeNameMap = Collections.synchronizedMap(new HashMap<>());
        for(int i = 0; i < numEpochs; i++) {
            System.out.println("Starting epoch: "+(i+1)+"/"+numEpochs);
            Set<String> newAssignees = new HashSet<>(allAssignees);
            newAssignees.removeAll(rawToNormalizedAssigneeNameMap.keySet());
            newAssignees.addAll(rawToNormalizedAssigneeNameMap.values());
            System.out.println("Looking thru "+newAssignees.size()+" assignees.");
            Map<String,Pair<String,Double>> tempMap = Collections.synchronizedMap(new HashMap<>());
            allAssignees = runIteration(newAssignees,tempMap,assigneeToPortfolioSizeMap, new JaroWinkler(), i);
            tempMap.entrySet().parallelStream().forEach(newEntry->{
                if(rawToNormalizedAssigneeNameMap.containsValue(newEntry.getKey())) {
                    // update
                    rawToNormalizedAssigneeNameMap.entrySet().forEach(oldEntry->{
                        if(oldEntry.getValue().equals(newEntry.getKey())) {
                            // update
                            rawToNormalizedAssigneeNameMap.put(oldEntry.getKey(),newEntry.getValue()._1);
                        }
                    });
                }
                if(rawToNormalizedAssigneeNameMap.containsKey(newEntry.getValue()._1)) {
                    rawToNormalizedAssigneeNameMap.remove(newEntry.getValue()._1);
                }
                rawToNormalizedAssigneeNameMap.put(newEntry.getKey(),newEntry.getValue()._1);
            });
            save(rawToNormalizedAssigneeNameMap,assigneeToPortfolioSizeMap);
        }

    }
}
