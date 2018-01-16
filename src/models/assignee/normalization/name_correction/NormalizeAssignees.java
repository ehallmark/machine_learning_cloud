package models.assignee.normalization.name_correction;

import com.googlecode.concurrenttrees.radix.ConcurrentRadixTree;
import com.googlecode.concurrenttrees.radix.RadixTree;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultCharSequenceNodeFactory;
import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.interfaces.StringDistance;
import models.assignee.database.MergeRawAssignees;
import seeding.Constants;
import seeding.Database;
import user_interface.ui_models.attributes.hidden_attributes.AssetToAssigneeMap;
import util.Pair;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by Evan on 10/8/2017.
 */
public class NormalizeAssignees {
   // private static Map<String,Pair<String,Double>> rawToNormalizedAssigneeNameMapWithScores;
    private static final File rawToNormalizedAssigneeNameFile = new File(Constants.DATA_FOLDER+"raw_to_normalized_assignee_name_map.jobj");

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

    static final Collection<String> manualBadPrefixes = Arrays.asList(
            "KABUSHIKI KAISHA ",
            "NTT ",
            "SK ",
            "UNITED STATES OF AMERICA AS REPRESENTED BY ",
            "THE "
    );

    static final Collection<String> manualBadSuffixes = Arrays.asList(
            " KABUSHIKI KAISHA",
            " LP",
            " GMBH",
            " S.A",
            " SA",
            " KG",
            " &",
            " SAS",
            " AS",
            " NV",
            " N.V",
            " EUROPE",
            " S.A.S",
            " B.V",
            " E.V",
            " PTY",
            " CO.,LTD.",
            " CO.,LTD",
            " CO"," CORP"," CORPS"," CORPORATION"," LLP", " CO.", " I", " II", " III", " IV", " V", " AG", " AB", " OY"," INCORPORATED"," LTD", " LIMITED", " INC", " CO LTD", " LLC"
    );

    private Map<String,String> rawToNormalizedMap;
    public NormalizeAssignees(Map<String,String> rawToNormalizedMap) {
        this.rawToNormalizedMap=rawToNormalizedMap;
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
        return rawToNormalizedMap.getOrDefault(assignee,assignee);
    }

    public static String manualCleanse(String cleanIsh) {
        String orig = cleanIsh;
        cleanIsh = cleanIsh.trim();

        if(cleanIsh.length() > MIN_ASSIGNEE_LENGTH && cleanIsh.contains(" ")) {
            // clean prefixes
            boolean prefixProblem = true;
            while(prefixProblem) {
                prefixProblem = false;
                for (String pref : manualBadPrefixes) {
                    if (cleanIsh.startsWith(pref) && cleanIsh.length() > pref.length() + MIN_ASSIGNEE_LENGTH) {
                        cleanIsh = cleanIsh.substring(pref.length()).trim();
                        prefixProblem = true;
                    }
                }
            }
            // clean suffixes
            boolean suffixProblem = true;
            while(suffixProblem) {
                while(cleanIsh.charAt(cleanIsh.length()-1)!=')'&&!Character.isAlphabetic(cleanIsh.charAt(cleanIsh.length()-1))) {
                    if(cleanIsh.length()==1) return null;
                    cleanIsh = cleanIsh.substring(0,cleanIsh.length()-1).trim();
                }

                suffixProblem = false;
                for (String suff : manualBadSuffixes) {
                    if (cleanIsh.endsWith(suff) && cleanIsh.length() > suff.length() + MIN_ASSIGNEE_LENGTH) {
                        cleanIsh = cleanIsh.substring(0, cleanIsh.length() - suff.length()).trim();
                        suffixProblem = true;
                    }
                }
            }


        }
        while(cleanIsh.charAt(cleanIsh.length()-1)!=')'&&!Character.isAlphabetic(cleanIsh.charAt(cleanIsh.length()-1))) {
            if(cleanIsh.length()==1) return null;
            cleanIsh = cleanIsh.substring(0,cleanIsh.length()-1).trim();
        }
        // check for manual changes
        cleanIsh = manualMerge(cleanIsh);
        System.out.println("Starting: "+orig+" => "+cleanIsh);
        return cleanIsh;
    }

    public static void saveAs(String name, Map<String,Pair<String,Double>> rawToNormalizedMap) {
        System.out.println("Starting to save: "+name);

        Map<String,String> rawToNormalizedAssigneeNameMap = rawToNormalizedMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(),e->e.getValue()._1));
        Database.trySaveObject(rawToNormalizedAssigneeNameMap,new File(name));
        // save to csv
        NormalizeAssignees normalizer = new NormalizeAssignees(rawToNormalizedAssigneeNameMap);
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

    public static void save(Map<String,String> rawToNormalizedMap) {
        saveAs(rawToNormalizedAssigneeNameFile.getAbsolutePath(), rawToNormalizedMap.entrySet().parallelStream().collect(Collectors.toMap(e->e.getKey(),e->new Pair<>(e.getValue(),0d))));
    }

    public static synchronized Map<String,String> getRawToNormalizedAssigneeNameMap() {
        return (Map<String,String>) Database.tryLoadObject(rawToNormalizedAssigneeNameFile);
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
                    if(!val.contains(" ")&&!assignee.contains(" ")) {
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
        RadixTree<String> radix = new ConcurrentRadixTree<>(new DefaultCharSequenceNodeFactory());
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

    private static String stripPrefixesAndSuffixes(String raw) {
        String[] badStrings = new String[]{
                "UNIVERSITY COURT OF THE ",
                "UNIVERSITY OF ",
                "NATIONAL INSTITUTE OF "
        };
        for(String badString : badStrings) {
            if (raw.startsWith(badString) && raw.length() > badString.length()) {
                raw = raw.substring(badString.length());
            }
        }
        return raw;
    }

    private static Collection<String> runIteration(Collection<String> allAssignees, Map<String,Pair<String,Double>> rawToNormalizedMap, Map<String,Integer> assigneeToPortfolioSizeMap, StringDistance distanceFunction, int epoch) {
        Set<String> badSuffixes = buildBadSuffixes(allAssignees);

        allAssignees = allAssignees.parallelStream().map(assignee->{
            if(assignee.length()<=1) return null;

            String newWord = manualCleanse(assignee);

            if(newWord==null) return null;

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
            int portfolioSize = assigneeToPortfolioSizeMap.getOrDefault(assignee,0);
            if(portfolioSize==0) System.out.println("Warning: portfolio size of 0 for "+assignee);
            if(newWord.equals(assignee)) {
                assigneeToPortfolioSizeMap.put(newWord, portfolioSize);
            } else {
                int newSize = assigneeToPortfolioSizeMap.getOrDefault(newWord, 0);
                assigneeToPortfolioSizeMap.put(newWord, portfolioSize+newSize);
            }
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

        int maxNumAssigneeSamples = 1000;
        Collection<String> largestAssigneeSamples = new HashSet<>(allAssignees.parallelStream()
                .map(a->new Pair<>(a,assigneeToPortfolioSizeMap.getOrDefault(a,0)))
                .filter(e->e._2>minPortfolioSize)
                .sorted((e1,e2)->e2._2.compareTo(e1._2))
                .limit(maxNumAssigneeSamples)
                .map(e->e._1).collect(Collectors.toList()));

        System.out.println("Total normalizations after prefix search: "+rawToNormalizedMap.size() + " / "+allAssignees.size());
        saveAs("test 0-"+epoch, rawToNormalizedMap);

        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger changes = new AtomicInteger(0);
        Collection<String> changedAssignees = new HashSet<>();
        allAssignees.parallelStream().filter(a->!largestAssigneeSamples.contains(a)).forEach(rawAssignee->{
            Pair<String,Double> mostSimilarCandidate = largestAssigneeSamples.stream().map(candidate->{
                return new Pair<>(candidate, distanceFunction.distance(stripPrefixesAndSuffixes(candidate), stripPrefixesAndSuffixes(rawAssignee)));
            }).reduce((p1,p2)->p1._2<p2._2 ? p1 : p2).get();
            double maxDistance = Math.min(0.1, 0.01 * Math.log(Math.E + Math.max(rawAssignee.split(" ").length, mostSimilarCandidate._1.split(" ").length)));
            // make sure that if begins with abbreviation, they are the same
            // do this by making sure a high degree of similarity in the first word
            String otherFirstWord = mostSimilarCandidate._1.split(" ")[0];
            String firstWord = rawAssignee.split(" ")[0];
            if (firstWord.length() > 5 || otherFirstWord.equals(firstWord)) {
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

        saveAs("test 1-"+epoch,rawToNormalizedMap);

        System.out.println("Total normalizations after distance search: "+rawToNormalizedMap.size() + " / "+allAssignees.size());

        return allAssignees;
    }

    private static Map<String,String> initialCleanse(Collection<String> assignees) {
        return assignees.parallelStream().map(a->new Pair<>(a,manualCleanse(a))).filter(p->p._2!=null).collect(Collectors.toMap(p->p._1,p->p._2));
    }

    private static Map<String,Integer> createPortfolioSizeMap(Collection<String> cleansed, Map<String,String> rawToCleansed, Map<String,Integer> portfolioSizeMap) {
         return rawToCleansed.entrySet().parallelStream().map(e->{
            return new Pair<>(portfolioSizeMap.getOrDefault(e.getKey(),0),e.getValue());
        }).collect(Collectors.groupingBy(e->e._2,Collectors.collectingAndThen(Collectors.reducing((e1,e2)->e1._1>e2._1?e1:e2),e->e.get()._1)));
    }

    private static Map<String,String> match(Collection<String> cleansed, Map<String,Integer> cleansedToSizeMap) {
        final AtomicInteger cnt = new AtomicInteger(0);
        final AtomicInteger matched = new AtomicInteger(0);
        boolean test = true;
        Map<String,Double> similarityCache = new ConcurrentHashMap<>();

        Collection<String> copyOfCleansed = new ArrayList<>(cleansed);
        return cleansed.parallelStream().map(name->{
            if(cnt.getAndIncrement()%1000==999) {
                System.out.println("Finished "+cnt.get()+" / "+copyOfCleansed.size()+" with "+matched.get()+" matches.");
            }
            JaroWinkler distance = new JaroWinkler();
            String[] words = name.split(" ");
            String strippedName = stripPrefixesAndSuffixes(name);

            int size = cleansedToSizeMap.getOrDefault(name,0);

            Pair<String,Double> best = copyOfCleansed.stream().map(other->{
                if(!other.contains(" ")&&!name.contains(" ")) return null;
                if(name.equals(other)) return null;

                String[] otherWords = other.split(" ");

                double matchThreshold = 0.95;
                double score = 0d;

                if(words[0].equals(otherWords[0])) {
                    matchThreshold-=0.05;
                    score+=0.05;
                }

               // String combinedName = String.join("___",Stream.of(name,other).sorted().collect(Collectors.toList()));
               // Double simCache = similarityCache.get(combinedName);
               // if(simCache==null) {
                    score += distance.similarity(strippedName, stripPrefixesAndSuffixes(other));
               //     similarityCache.put(combinedName,score);
               // } else {
               //     score = simCache;
               // }

                if(score>=matchThreshold) {
                    // adjust score for portfolio size
                    score *= Math.log(Math.E+Math.abs(cleansedToSizeMap.getOrDefault(other,0)-size));
                    return new Pair<>(other,score);
                }
                return null;
            }).filter(p->p!=null).max(Comparator.comparing(p->p._2)).orElse(null);
            if(best!=null) {
                int sizeThis = size;
                int sizeThat = cleansedToSizeMap.getOrDefault(best._1,0);
                if(sizeThat > sizeThis) {
                    if(test) {
                        System.out.println(" MATCH: "+name+" => "+best._1);
                    }
                    matched.getAndIncrement();
                    return new Pair<>(name,best._1);
                }
            }
            return null;
        }).filter(p->p!=null).collect(Collectors.toMap(p->p._1,p->p._2));
    }

    private static Map<String,String> groupBy(Map<String,String> rawToCleansed, Map<String,String> cleansedToNormalized) {
        return rawToCleansed.entrySet().parallelStream()
                .map(e->new Pair<>(e.getKey(),cleansedToNormalized.getOrDefault(e.getValue(),e.getValue())))
                .collect(Collectors.toMap(e->e._1,e->e._2));
    }

    private static Map<String,String> performIteration(Collection<String> companies, Map<String,Integer> assigneeToPortfolioSizeMap) {
        // initial cleanse
        System.out.println("Starting initial cleanse...");
        System.out.println("Non cleansed: "+companies.size());
        Map<String,String> rawToCleansed = initialCleanse(companies);

        Set<String> cleansed = Collections.synchronizedSet(new HashSet<>(rawToCleansed.values()));
        System.out.println("Cleansed: "+cleansed.size());

        System.out.println("Building portfolio size map...");
        Map<String,Integer> cleansedToSize = createPortfolioSizeMap(cleansed,rawToCleansed,assigneeToPortfolioSizeMap);

        System.out.println("Portfolio map size: "+cleansedToSize.size());

        System.out.println("Starting to normalize...");
        // match cleansed
        Map<String,String> cleansedToNormalized = match(cleansed,cleansedToSize);
        System.out.println("Normalized map size: "+cleansedToNormalized.size());

        System.out.println("Starting final grouping...");
        // regroup with raw names
        return groupBy(rawToCleansed,cleansedToNormalized);
    }

    public static void main(String[] args) {
        run(MergeRawAssignees.get());

        boolean test = true;

        if(!test) {
            System.out.println("Saving assignee map...");
            new AssetToAssigneeMap().save();
        }

    }

    public static void run(Map<String,Map<String,Object>> assigneeData) {
        Set<String> foreignCompanies = Collections.synchronizedSet(new HashSet<>());
        Set<String> domesticCompanies = Collections.synchronizedSet(new HashSet<>());

        // collect companies that are not business organizations or humans
        Collection<String> allCompanies = Collections.synchronizedList(assigneeData.entrySet().parallelStream()
                .filter(e->{
                    String assignee = e.getKey();
                    Map<String,Object> data = e.getValue();
                    Boolean isHuman = (Boolean)data.get(Constants.IS_HUMAN);
                    if(isHuman==null||isHuman) return false;
                    String role = (String)data.get(Constants.ASSIGNEE_ROLE);
                    if(role==null) return false;
                    if(role.endsWith("2")) {
                        // us
                        domesticCompanies.add(assignee);
                        return true;
                    } else if(role.endsWith("3")) {
                        // foreign
                        foreignCompanies.add(assignee);
                        return true;
                    }
                    return false;
                })
                .map(e->e.getKey())
                .collect(Collectors.toList()));

        System.out.println("Num foreign companies: "+foreignCompanies.size());
        System.out.println("Num domestic companies: "+domesticCompanies.size());

        Map<String,Integer> assigneeToPortfolioSizeMap = Collections.synchronizedMap(new HashMap<>(allCompanies
                .parallelStream()
                .collect(Collectors.toMap(a->a,a->Math.max(Database.getAssetCountFor(a),Database.getNormalizedAssetCountFor(a))))));
        System.out.println("Num assignees with portfolio size: "+assigneeToPortfolioSizeMap.size());

        // regroup with raw names
        Map<String,String> rawToNormalizedForeign = performIteration(foreignCompanies,assigneeToPortfolioSizeMap);
        Map<String,String> rawToNormalizedDomestic = performIteration(domesticCompanies,assigneeToPortfolioSizeMap);

        Map<String,String> rawToNormalized = Collections.synchronizedMap(new HashMap<>(rawToNormalizedDomestic.size()+rawToNormalizedForeign.size()));
        rawToNormalized.putAll(rawToNormalizedForeign);
        rawToNormalized.putAll(rawToNormalizedDomestic);

        save(rawToNormalized);
    }
}
