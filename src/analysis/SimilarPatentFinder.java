package analysis;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicDouble;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;
import org.deeplearning4j.text.tokenization.tokenizerfactory.DefaultTokenizerFactory;
import org.deeplearning4j.text.tokenization.tokenizerfactory.TokenizerFactory;
import org.eclipse.jetty.util.ArrayQueue;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import seeding.*;
import tools.*;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import server.tools.AbstractPatent;

/**
 * Created by ehallmark on 7/26/16.
 */
public class SimilarPatentFinder {
    protected MinHeap<Patent> heap;
    protected List<Patent> patentList;
    protected String name;
    private static TokenizerFactory tf = new DefaultTokenizerFactory();
    private static Map<String,INDArray> globalCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,List<String>> patentWordsCache = Collections.synchronizedMap(new HashMap<>());
    private static Map<String,INDArray> globalCandidateAvgCache = Collections.synchronizedMap(new HashMap<>());
    static {
        tf.setTokenPreProcessor(new MyPreprocessor());
    }

    public static Map<String,INDArray> getGlobalCache() {
        return globalCache;
    }

    //private Map<String, String> assigneeMap;

    public SimilarPatentFinder(Map<String,Pair<Float,INDArray>> vocab) throws SQLException, IOException, ClassNotFoundException {
        this(null, new File(Constants.PATENT_VECTOR_LIST_FILE), "**ALL**", vocab);
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException,ClassNotFoundException {
        this(candidateSet,patentListFile,name,null, vocab);
    }

    public SimilarPatentFinder(String name,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        this(name, getVectorFromDB(name, vocab));
    }

    public SimilarPatentFinder(String name, INDArray data) throws SQLException {
        this.name=name;
        patentList = data==null?null:Arrays.asList(new Patent(name, data));
    }

    public SimilarPatentFinder(List<String> candidateSet, File patentListFile, String name, INDArray eigenVectors, Map<String,Pair<Float,INDArray>> vocab) throws SQLException,IOException, ClassNotFoundException {
        // construct lists
        this.name=name;
        System.out.println("--- Started Loading Patent Vectors ---");
        if (!patentListFile.exists()) {
            try {
                if (candidateSet == null) {
                    candidateSet = Database.getValuablePatentsToList();
                }
                int arrayCapacity = candidateSet.size();
                patentList = new ArrayList<>(arrayCapacity);
                // go thru candidate set and remove all that we can find
                List<String> toRemove = new ArrayList<>();
                for(String patent : candidateSet) {
                    if(globalCache.containsKey(patent)) {
                        patentList.add(new Patent(patent,globalCache.get(patent)));
                        toRemove.add(patent);
                        System.out.println("Found: "+patent);
                    }
                }
                candidateSet.removeAll(toRemove);
                if(!candidateSet.isEmpty()) {
                    ResultSet rs;
                    rs = Database.selectPatentVectors(candidateSet);
                    int count = 0;
                    int offset = 2; // Due to the pub_doc_number field
                    while (rs.next()) {
                        try {
                            INDArray array = handleResultSet(rs, offset, vocab);
                            if (array != null) {
                                patentList.add(new Patent(rs.getString(1), array));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        System.out.println(++count);
                    }
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            // Serialize List
            ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(patentListFile)));
            oos.writeObject(patentList);
            oos.flush();
            oos.close();
        } else {
            // read from file
            ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(patentListFile)));
            Object obj = ois.readObject();
            patentList = ((List<Patent>) obj);
            // PCA
            if(eigenVectors!=null) patentList.forEach(p->p.getVector().mmuli(eigenVectors));
            ois.close();
        }

        // Now add stuff to the map
        patentList.forEach(p->{
            globalCache.put(p.getName(),p.getVector());
        });

        System.out.println("--- Finished Loading Patent Vectors ---");
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(int limit, Map<String,Pair<Float,INDArray>> vocab, String patent) throws SQLException {
        if(patentWordsCache.containsKey(patent)) return predictKeywords(patentWordsCache.get(patent),limit,vocab);
        // otherwise, if not cached
        ResultSet rs = Database.getBaseVectorFor(patent);
        if(rs.next()) {
            List<String> tokens = new ArrayList<>();
            tokens.addAll(tf.create(rs.getString(1)).getTokens());
            tokens.addAll(tf.create(rs.getString(2)).getTokens());
            tokens.addAll(tf.create(rs.getString(3)).getTokens());
            patentWordsCache.put(patent, tokens);
            return predictKeywords(tokens,limit,vocab);
        } else {
            return null;
        }
    }

    private static List<WordFrequencyPair<String,Float>> predictKeywordsForMultiple(int limit, Map<String,Pair<Float,INDArray>> vocab, List<Patent> patents) throws SQLException {
        List<String> tokens = new ArrayList<>();
        for(Patent p : patents) {
            String patent = p.getName();
            System.out.println("    Predicting "+patent);
            if(patentWordsCache.containsKey(patent)) {
                tokens.addAll(patentWordsCache.get(patent));
            } else {
                // otherwise, if not cached
                ResultSet rs = Database.getBaseVectorFor(patent);
                if (rs.next()) {
                    tokens.addAll(tf.create(rs.getString(1)).getTokens());
                    tokens.addAll(tf.create(rs.getString(2)).getTokens());
                    tokens.addAll(tf.create(rs.getString(3)).getTokens());
                    patentWordsCache.put(patent, tokens);
                }
            }
        }
        return predictKeywords(tokens,limit,vocab,computeAvg(patents,null));
    }

    public List<Map.Entry<String,Pair<Integer,Set<String>>>> autoClassify(Map<String,Pair<Float,INDArray>> vocab) throws Exception {
        // to do
        TreeSet<Patent> set = new TreeSet<>((p1,p2)->p1.getName().compareTo(p2.getName()));
        Map<String,List<WordFrequencyPair<String,Float>>> cache = new HashMap<>();
        final int consideredPerElement = 25;
        final int maxNumPerIteration = 6;
        final int maxNumberOfIterations = 10;

        patentList.forEach(p->{
            set.add(new Patent(p.getName(),p.getVector()));
            System.out.println(p.getName());
            try {
                cache.put(p.getName(),predictKeywords(consideredPerElement,vocab,p.getName()));
            } catch(Exception e) {
                e.printStackTrace();
            }
        });
        AtomicInteger cnt = new AtomicInteger(0);
        Map<String,Pair<Integer,Set<String>>> classMap = new HashMap<>();
        while(!set.isEmpty()) {
            List<Patent> patents = new ArrayList<>();
            for(int i = 0; i < maxNumPerIteration; i++) {
                if(set.isEmpty()) break;
                int sizeBefore = set.size();
                Patent p = set.pollFirst();
                int sizeAfter = set.size();
                assert (sizeAfter < sizeBefore) : "Poll first is not removing element!";
                patents.add(p);
            }
            System.out.println("Starting to get results on iteration: "+cnt.get());
            List<WordFrequencyPair<String,Float>> results = predictKeywordsForMultiple(1,vocab,patents);
            System.out.println("Checking results on iteration: "+cnt.get());
            for(int i = 0; i < results.size(); i++) {
                List<Patent> toRemove = new ArrayList<>();
                for(Patent p : set) {
                    System.out.println("    Checking "+p.getName());
                    for(WordFrequencyPair<String,Float> check : cache.get(p.getName())) {
                        if(results.get(i).getFirst().equals(check.getFirst())) {
                            if(classMap.containsKey(check.getFirst())) {
                                classMap.get(check.getFirst()).getSecond().add(p.getName());
                            } else {
                                classMap.put(check.getFirst(),new Pair<>(classMap.size()+1,Sets.newHashSet(p.getName())));
                            }
                            toRemove.add(p);
                            break;
                        }
                    }
                }
                set.removeAll(toRemove);
            }
            if(cnt.getAndIncrement()>=maxNumberOfIterations) {
                break;
            }

        }
        if(!set.isEmpty()) {
            Set<String> toAdd = new HashSet<>();
            for (Patent p : set) {
                toAdd.add(p.getName());
            }
            classMap.put("**UNABLE TO CLASSIFY**",new Pair<>(classMap.size()+1,toAdd));
        }
        return classMap.entrySet().stream().sorted((e1,e2)->e1.getValue().getFirst().compareTo(e2.getValue().getFirst())).collect(Collectors.toList());
    }

    private static void processNGrams(List<String> tokens, INDArray docVector, Map<String,AtomicDouble> nGramCounts, Map<String,Pair<Set<String>,AtomicDouble>> stemmedCounts, Map<String,Pair<Float,INDArray>> vocab, int n) {
        assert n >= 1 : "Cannot process n grams if n < 1!";
        Stemmer stemMe = new Stemmer();
        Map<String,Set<String>> newToks = new HashMap<>();
        List<String> cleanToks = tokens.stream().filter(s->s!=null).collect(Collectors.toList());
        Set<String> permutationsSet = new HashSet<>();
        Map<String,Set<String>> shiftedStems = new HashMap<>();
        int tIdx = 0;
        for(int i = 0; i < cleanToks.size()-n; i++) {
            List<String> sub = cleanToks.subList(i,i+n);
            int nullCount = 0;
            for(int j = tIdx; j < tIdx+n; j++) {
                if(tokens.get(j)==null) {
                    nullCount++;
                }
            }
            tIdx+=1+(tokens.get(tIdx)==null?1:0);
            if(((int)sub.stream().map(s->stemMe.stem(s)).distinct().count())!=sub.size()) {
                continue;
            }

            INDArray toAvg = Nd4j.create(sub.size(), Constants.VECTOR_LENGTH);
            AtomicInteger cnt = new AtomicInteger(0);
            AtomicDouble freq = new AtomicDouble(0.0);
            sub.forEach(s->{
                Pair<Float,INDArray> word = vocab.get(s);
                freq.getAndAdd(word.getFirst());
                toAvg.putRow(cnt.getAndIncrement(),word.getSecond());
            });
            String next = String.join(" ",sub);
            List<String> stemSub = sub.stream().map(s->stemMe.stem(s)).collect(Collectors.toList());
            String stemmedNext = String.join(" ", stemSub);
            List<String> permutedStems = new Permutations<String>().permute(stemmedNext.split(" ")).stream().map(perm->String.join(" ",perm)).sorted().collect(Collectors.toList());
            // add next sequence if possible
            if(i < cleanToks.size()-n-1 && n > 1) {
                String link = String.join(" ",stemSub.subList(1,n))+" "+stemMe.stem(cleanToks.get(i+n));
                if(shiftedStems.containsKey(stemmedNext)) {
                    shiftedStems.get(stemmedNext).add(link);
                } else {
                    Set<String> hash = new HashSet<>();
                    hash.add(link);
                    shiftedStems.put(stemmedNext,hash);
                }
            }
            if(permutedStems.size()>0)permutationsSet.add(String.join(",",permutedStems));
            if(newToks.containsKey(stemmedNext)) {
                newToks.get(stemmedNext).add(next);
            } else {
                Set<String> hash = new HashSet<>();
                hash.add(next);
                newToks.put(stemmedNext, hash);
            }
            double weight = (Math.pow(n,1.5)/(nullCount+1))*Transforms.cosineSim(docVector,toAvg.mean(0))*freq.get();
            if(nGramCounts.containsKey(next)) {
                nGramCounts.get(next).getAndAdd(weight);
            } else {
                nGramCounts.put(next, new AtomicDouble(weight));
            }
            if(stemmedCounts.containsKey(stemmedNext)) {
                Pair<Set<String>,AtomicDouble> p = stemmedCounts.get(stemmedNext);
                p.getSecond().getAndAdd(weight);
                p.getFirst().add(next);
            } else {
                stemmedCounts.put(stemmedNext, new Pair<>(newToks.get(stemmedNext),new AtomicDouble(weight)));
            }
        }
        for(Map.Entry<String,Pair<Set<String>,AtomicDouble>> e : stemmedCounts.entrySet()) {
            for (Map.Entry<String, Set<String>> newTok : newToks.entrySet()) {
                double stemValue = stemmedCounts.get(newTok.getKey()).getSecond().get();
                if (e.getKey().contains(newTok.getKey()) && e.getKey().length() > newTok.getKey().length()) {
                    if (e.getValue().getSecond().get() >= stemValue) {
                        for (String toRemove : newTok.getValue()) {
                            if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
                        }
                    } else {
                        for(String toRemove : e.getValue().getFirst()) {
                            if(nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
                        }
                    }
                }
            }
        }

        for(Map.Entry<String,Set<String>> newTok : newToks.entrySet()) {
            double stemValue = stemmedCounts.get(newTok.getKey()).getSecond().get();
            List<String> data = newTok.getValue().stream().filter(s -> nGramCounts.containsKey(s)).map(s -> new WordFrequencyPair<>(s, nGramCounts.get(s).get())).sorted().map(p -> p.getFirst()).collect(Collectors.toList());
            for (int i = 0; i < data.size() - 1; i++) {
                String toRemove = data.get(i);
                if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
            }
            if(!data.isEmpty())nGramCounts.get(data.get(data.size() - 1)).set(stemValue);
        }

        for (String tok : permutationsSet) {
            List<WordFrequencyPair<String, Double>> data = new ArrayList<>();
            if (tok == null || tok.split(",") == null || tok.split(",").length == 0) continue;
            for (String permStem : Arrays.asList(tok.split(","))) {
                if (permStem == null || permStem.length() == 0) continue;
                if (newToks.containsKey(permStem)) {
                    for (String ngram : newToks.get(permStem)) {
                        if (ngram == null) continue;
                        if (nGramCounts.containsKey(ngram)) {
                            data.add(new WordFrequencyPair<>(ngram, nGramCounts.get(ngram).get()));
                        }
                    }
                }
            }
            data = data.stream().distinct().filter(k->nGramCounts.containsKey(k.getFirst())).sorted().collect(Collectors.toList());
            for (int i = 0; i < data.size() - 1; i++) {
                String toRemove = data.get(i).getFirst();
                nGramCounts.remove(toRemove);
            }
            if (!data.isEmpty())
                nGramCounts.get(data.get(data.size() - 1).getFirst()).set(data.stream().collect(Collectors.summingDouble(d -> d.getSecond())));
        }

        for(Map.Entry<String,Set<String>> shift : shiftedStems.entrySet()) {
            if(!newToks.containsKey(shift.getKey())) continue;
            List<WordFrequencyPair<String,Double>> data = new ArrayList<>();
            for(String actual : newToks.get(shift.getKey())) {
                if (nGramCounts.containsKey(actual)) {
                    data.add(new WordFrequencyPair<>(actual,nGramCounts.get(actual).get()));
                }
            }
            for(String stem : shift.getValue().stream().filter(s -> newToks.containsKey(s)).collect(Collectors.toList())) {
                for(String actual : newToks.get(stem)) {
                    if (nGramCounts.containsKey(actual)) {
                        data.add(new WordFrequencyPair<>(actual,nGramCounts.get(actual).get()));
                    }
                }
            }
            data = data.stream().distinct().sorted().collect(Collectors.toList());
            for (int i = 0; i < data.size() - 1; i++) {
                String toRemove = data.get(i).getFirst();
                if (nGramCounts.containsKey(toRemove)) nGramCounts.remove(toRemove);
            }
            if(!data.isEmpty()){
                WordFrequencyPair<String,Double> lastPair = data.get(data.size()-1);
                nGramCounts.get(lastPair.getFirst()).set(lastPair.getSecond());
            }
        }

    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(String text, int limit, Map<String,Pair<Float,INDArray>> vocab) {
        return predictKeywords(tf.create(text).getTokens(),limit,vocab);
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(List<String> tokens, int limit, Map<String,Pair<Float,INDArray>> vocab) {
        return predictKeywords(tokens,limit,vocab,null);
    }

    public static List<WordFrequencyPair<String,Float>> predictKeywords(List<String> tokens, int limit, Map<String,Pair<Float,INDArray>> vocab, INDArray docVector) {
        Map<String,AtomicDouble> nGramCounts = new HashMap<>();
        Map<String,Pair<Set<String>,AtomicDouble>> stemmedCounts = new HashMap<>();
        tokens = tokens.stream().map(s->s!=null&&s.trim().length()>0&&!Constants.STOP_WORD_SET.contains(s)&&vocab.containsKey(s)?s:null).collect(Collectors.toList());
        if(docVector==null) docVector= VectorHelper.TFIDFcentroidVector(vocab,tokens.stream().filter(t->t!=null).collect(Collectors.toList()));
        final int maxLength = 3;
        for(int i = maxLength; i >= 1; i--) {
            processNGrams(tokens,docVector,nGramCounts,stemmedCounts,vocab,i);
        }

        MinHeap<WordFrequencyPair<String,Float>> heap = MinHeap.setupWordFrequencyHeap(limit);
        Stream<WordFrequencyPair<String,Float>> stream = nGramCounts.entrySet().stream().map(e->{
            WordFrequencyPair<String,Float> newPair = new WordFrequencyPair<>(e.getKey(),(float)e.getValue().get());
            return newPair;
        });
        stream.forEach(s->{
            heap.add(s);
        });

        List<WordFrequencyPair<String,Float>> results = new ArrayList<>(limit);
        while(!heap.isEmpty()) {
            WordFrequencyPair<String,Float> pair = heap.remove();
            results.add(0, pair);
        }

        return results;
    }

    public List<Patent> getPatentList() {
        return patentList;
    }

    public static INDArray handleResultSet(ResultSet rs, int offset, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        List<String> tokens = new ArrayList<>();
        String description = rs.getString(offset);
        RecursiveTask<List<String>> descTask = new GetTokensThread(tf,description);
        descTask.fork();
        String abstractText = rs.getString(offset+1);
        RecursiveTask<List<String>> absTask = new GetTokensThread(tf,abstractText);
        absTask.fork();
        String claims = rs.getString(offset+2);
        RecursiveTask<List<String>> claimTask = new GetTokensThread(tf,claims);
        claimTask.fork();
        tokens.addAll(descTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(absTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        tokens.addAll(claimTask.join().stream().filter(word->vocab.containsKey(word)).collect(Collectors.toList()));
        return VectorHelper.TFIDFcentroidVector(vocab,tokens);
    }

    public String getName() {
        return name;
    }

    private void setupMinHeap(int capacity) {
        heap = MinHeap.setupPatentHeap(capacity);
    }

    public static INDArray computeAvg(List<Patent> patentList, String candidateSetName) {
        if(candidateSetName!=null&&globalCandidateAvgCache.containsKey(candidateSetName)) {
            return globalCandidateAvgCache.get(candidateSetName);
        }
        INDArray thisAvg = Nd4j.create(patentList.size(),Constants.VECTOR_LENGTH);
        for(int i = 0; i < patentList.size(); i++) {
            thisAvg.putRow(i, patentList.get(i).getVector());
        }
        INDArray avg = thisAvg.mean(0);
        if(candidateSetName!=null) {
            globalCandidateAvgCache.put(candidateSetName,avg);
        }
        return avg;
    }

    public List<PatentList> similarFromCandidateSets(List<SimilarPatentFinder> others, double threshold, int limit, boolean findDissimilar) throws SQLException {
        List<PatentList> list = new ArrayList<>(others.size());
        others.forEach(other->{
            try {
                list.addAll(similarFromCandidateSet(other, threshold, limit, findDissimilar));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        });
        return list;
    }

    public List<PatentList> similarFromCandidateSet(SimilarPatentFinder other, double threshold, int limit, boolean findDissimilar) throws SQLException {
        // Find the highest (pairwise) assets
        if(other.getPatentList()==null||other.getPatentList().isEmpty()) return new ArrayList<>();
        List<PatentList> lists = new ArrayList<>();
        INDArray otherAvg = computeAvg(other.patentList,other.getName());
        Set<String> dontMatch = other.name.equals(this.name) ? null : other.patentList.stream().map(p->p.getName()).collect(Collectors.toSet());
        try {
            if(findDissimilar) lists.addAll(findOppositePatentsTo(other.name, otherAvg, dontMatch, threshold, limit));
            else lists.addAll(findSimilarPatentsTo(other.name, otherAvg, dontMatch, threshold, limit));

        } catch(SQLException sql) {
        }
        return lists;
    }

    /*private static void mergePatentLists(List<PatentList> patentLists, int limit) {
        PriorityQueue<AbstractPatent> queue = new PriorityQueue<>();
        for(PatentList list: patentLists) {
            queue.addAll(list.getPatents());
        }
        patentLists.clear();
        patentLists.add(new PatentList(new ArrayList<>(queue).subList(Math.max(0,queue.size()-limit-1), queue.size()-1)));
    }*/



    // returns null if patentNumber not found
    public List<PatentList> findSimilarPatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        assert patentNumber!=null : "Patent number is null!";
        assert heap!=null : "Heap is null!";
        assert patentList!=null : "Patent list is null!";
        if(avgVector==null) return new ArrayList<>();
        long startTime = System.currentTimeMillis();
        if(patentNamesToExclude ==null) {
            patentNamesToExclude=new HashSet<>();
            if(patentNumber!=null)patentNamesToExclude.add(patentNumber);
        }
        final Set<String> otherSet = Collections.unmodifiableSet(patentNamesToExclude);

        setupMinHeap(limit);
        List<PatentList> lists = Arrays.asList(similarPatentsHelper(patentList,avgVector, otherSet, name, patentNumber, threshold, limit));

        long endTime = System.currentTimeMillis();
        double time = new Double(endTime-startTime)/1000;
        System.out.println("Time to find similar patents: "+time+" seconds");

        return lists;
    }

    public Double angleBetweenPatents(String name1, String name2, Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray first = getVectorFromDB(name1,vocab);
        INDArray second = getVectorFromDB(name2,vocab);
        if(first!=null && second!=null) {
            //valid
            return Transforms.cosineSim(first,second);
        } else return null;
    }

    // returns null if patentNumber not found
    public List<PatentList> findOppositePatentsTo(String patentNumber, INDArray avgVector, Set<String> patentNamesToExclude, double threshold, int limit) throws SQLException {
        List<PatentList> toReturn = findSimilarPatentsTo(patentNumber, avgVector.mul(-1.0), patentNamesToExclude, threshold, limit);
        for(PatentList l : toReturn) {
            l.flipAvgSimilarity();
            l.getPatents().forEach(p->p.flipSimilarity());
        }
        return toReturn;
    }

    private static INDArray getVectorFromDB(String patentNumber,INDArray eigenVectors,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        INDArray avgVector = null;
        // first look in own patent list
        if(globalCache.containsKey(patentNumber)) avgVector=globalCache.get(patentNumber);

        if(avgVector==null) {
            ResultSet rs = Database.getBaseVectorFor(patentNumber);
            if (!rs.next()) {
                return null; // nothing found
            }
            int offset = 1;
            avgVector = handleResultSet(rs, offset, vocab);
            if(avgVector!=null) globalCache.put(patentNumber,avgVector);
        }
        if(eigenVectors!=null&&avgVector!=null) avgVector.mmuli(eigenVectors);
        return avgVector;
    }

    public static INDArray getVectorFromDB(String patentNumber,Map<String,Pair<Float,INDArray>> vocab) throws SQLException {
        return getVectorFromDB(patentNumber, null, vocab);
    }

    private synchronized PatentList similarPatentsHelper(List<Patent> patentList, INDArray baseVector, Set<String> patentNamesToExclude, String name1, String name2, double threshold, int limit) {
        Patent.setBaseVector(baseVector);
        //AtomicDouble total = new AtomicDouble(0.0);
        //AtomicInteger cnt = new AtomicInteger(0);
        patentList.forEach(patent -> {
            if(patent!=null&&!patentNamesToExclude.contains(patent.getName())) {
                patent.calculateSimilarityToTarget();
                //total.getAndAdd(patent.getSimilarityToTarget());
                //cnt.getAndIncrement();
                if(patent.getSimilarityToTarget() >= threshold)heap.add(patent);
            }
        });
        List<AbstractPatent> resultList = new ArrayList<>(limit);
        while (!heap.isEmpty()) {
            Patent p = heap.remove();
            //String assignee = assigneeMap.get(p.getName());
            //if(assignee==null)assignee="";
            try {
                resultList.add(0, Patent.abstractClone(p, null));
            } catch(SQLException sql) {
                sql.printStackTrace();
            }
        }
        //double avgSim = cnt.get() > 0 ? total.get()/cnt.get() : 0.0;
        PatentList results = new PatentList(resultList,name1,name2,Transforms.cosineSim(baseVector,computeAvg(patentList,name1)));
        return results;
    }

    // unit test!
    public static void main(String[] args) throws Exception {
        /*try {
            Database.setupSeedConn();
            SimilarPatentFinder finder = new SimilarPatentFinder(null, new File("candidateSets/3"), "othername");
            System.out.println("Most similar: ");
            PatentList list;// = finder.findSimilarPatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
            System.out.println("Most opposite: ");
            list = finder.findOppositePatentsTo("7455590", -1.0, 25).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }



            System.out.println("Candidate set comparison: ");
            list = finder.similarFromCandidateSet(new SimilarPatentFinder(null, new File("candidateSets/2"), "name"),0.0,20,false).get(0);
            for (AbstractPatent abstractPatent : list.getPatents()) {
                System.out.println(abstractPatent.getName()+": "+abstractPatent.getSimilarity());
            }
        } catch(Exception e) {
            e.printStackTrace();
        }*/
    }
}
