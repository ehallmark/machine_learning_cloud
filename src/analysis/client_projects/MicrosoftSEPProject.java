package analysis.client_projects;

import analysis.patent_view_api.PatentAPIHandler;
import dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import dl4j_neural_nets.iterators.sequences.DatabaseTextIterator;
import seeding.Database;
import seeding.GetEtsiPatentsList;

import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/9/2017.
 */
public class MicrosoftSEPProject {
    private static List<String> loadKeywordFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        List<String> keywords = reader.lines().filter(line->line!=null&&line.trim().length()>0).map(line->line.trim()).collect(Collectors.toList());
        System.out.println("Keywords found: "+String.join("; ",keywords));
        reader.close();
        return keywords;
    }

    private static void runCPCModel(File outputFile, List<String> patents, final int samplingRatio) throws IOException {
        // load patents
        Set<String> isSEP = new HashSet<>(patents);

        // add in samples
        {
            List<Integer> patNums = patents.stream().filter(p->{
                try{
                    Integer.valueOf(p);
                    return true;
                } catch(Exception e) {
                    return false;
                }
            }).map(p->Integer.valueOf(p)).collect(Collectors.toList());
            double avg = patNums.stream().collect(Collectors.averagingDouble(i->i.doubleValue()));
            int minPatNum =(int) (avg-(avg-Collections.min(patNums))/2);
            int maxPatNum =(int) (avg+(Collections.max(patNums)-avg)/2);
            Random rand = new Random(System.currentTimeMillis());
            Set<String> alreadyFound = new HashSet<>(patents);
            for(int i = 0; i < isSEP.size()*samplingRatio; i++) {
                int nextRand = minPatNum+(Math.abs(rand.nextInt())%(maxPatNum-minPatNum));
                System.out.println("Random patent: "+nextRand);
                if(!alreadyFound.contains(String.valueOf(nextRand))) {
                    patents.add(String.valueOf(nextRand));
                    alreadyFound.add(String.valueOf(nextRand));
                } else {
                    i--;
                }
            }
        }
        // now we have samples so we can pull data from patentview
        List<String> relevantClasses = new ArrayList<>();
        Map<String,Set<String>> classMap = new HashMap<>();
        Map<String,Double> classScoreMap = new HashMap<>();
        {
            Set<String> relevantClassSet = new HashSet<>();
            int batchSize = 500;
            for(int i = 0; i < patents.size()-batchSize; i+=batchSize) {
                PatentAPIHandler.requestAllPatents(patents.subList(i,i+batchSize)).forEach(patent -> {
                    Set<String> classes = patent.getClassCodes().stream()
                            .map(c->c.getSubgroup()).collect(Collectors.toSet());
                    if(isSEP.contains(patent.getPatentNumber())) {
                        relevantClassSet.addAll(classes);
                        classes.forEach(clazz->{
                            if(classScoreMap.containsKey(clazz)) {
                                classScoreMap.put(clazz,classScoreMap.get(clazz)+1.0);
                            } else {
                                classScoreMap.put(clazz,1.0);
                            }
                        });
                    }
                    if(!classes.isEmpty()) classMap.put(patent.getPatentNumber(),classes);
                });
            }
            relevantClasses.addAll(relevantClassSet);
            relevantClassSet.clear();
        }
        // load data
        Map<String,Double[]> patentToDataMap = new HashMap<>();
        Random rand = new Random(System.currentTimeMillis());
        int limit = 100;
        List<String> sortedClasses = relevantClasses.stream().sorted((c1,c2)->classScoreMap.get(c2).compareTo(classScoreMap.get(c1)))
                .limit(limit).collect(Collectors.toList());

        patents.forEach(patent->{
            System.out.println("Starting patent: "+patent);
            Double[] data = new Double[1+sortedClasses.size()];
            data[0] = isSEP.contains(patent) ? 1.0 : 0.0; // SEP status
            Set<String> classes = classMap.get(patent);
            if(classes==null||classes.isEmpty()||!classes.stream().anyMatch(c->relevantClasses.contains(c))) return;
            for(int i = 0; i < sortedClasses.size(); i++) {
                if(classes.contains(sortedClasses.get(i))) {
                    data[i+1]=1.0/classes.size();//+(rand.nextGaussian()/100);
                } else {
                    data[i+1]=0.0;//+(rand.nextGaussian()/100);
                }
            }
            patentToDataMap.put(patent, data);
        });



        List<String> headers = new ArrayList<>();
        //headers.add("asset");
        headers.add("is_sep");
        for(String keyword : sortedClasses) {
            headers.add(keyword);
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        // write headers
        StringJoiner headerLine = new StringJoiner(",","","\n");
        for(String header : headers) {
            headerLine.add(header.toLowerCase().replaceAll(" ","_").replaceAll(",",""));
        }
        bw.write(headerLine.toString());
        // write data
        patentToDataMap.forEach((patent,data)->{
            StringJoiner line = new StringJoiner(",","","\n");
            //line.add(patent);
            for(Double value : data) {
                line.add(value.toString());
            }
            synchronized (bw) {
                try {
                    bw.write(line.toString());
                    bw.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        bw.close();
    }

    private static void runKeywordModel(File outputFile, File keywordFile, List<String> patents, final int samplingRatio, final int keywordLimit) throws IOException,SQLException {
        // load keywords
        List<String> keywords = loadKeywordFile(keywordFile).stream()
                .map(keyword->keyword.toLowerCase().replaceAll("_"," ").trim())
                .limit(keywordLimit)
                .collect(Collectors.toList());

        // load patents
        Set<String> isSEP = new HashSet<>(patents);

        // add in samples
        {
            List<Integer> patNums = patents.stream().filter(p->{
                try{
                    Integer.valueOf(p);
                    return true;
                } catch(Exception e) {
                    return false;
                }
            }).map(p->Integer.valueOf(p)).collect(Collectors.toList());
            double avg = patNums.stream().collect(Collectors.averagingDouble(i->i.doubleValue()));
            int minPatNum =(int) (avg-(avg-Collections.min(patNums))/2);
            int maxPatNum =(int) (avg+(Collections.max(patNums)-avg)/2);
            Random rand = new Random(System.currentTimeMillis());
            for(int i = 0; i < isSEP.size()*samplingRatio; i++) {
                int nextRand = minPatNum+(Math.abs(rand.nextInt())%(maxPatNum-minPatNum));
                System.out.println("Random patent: "+nextRand);
                if(!isSEP.contains(String.valueOf(nextRand))) {
                    patents.add(String.valueOf(nextRand));
                } else {
                    i--;
                }
            }
        }
        // load data
        Map<String,Double[]> patentToDataMap = new HashMap<>();
        DatabaseTextIterator iterator = DatabaseIteratorFactory.SpecificPatentParagraphTextIterator(patents);
        while(iterator.hasNext()) {
            String sentence = iterator.nextSentence();
            if(sentence==null||sentence.isEmpty()) continue;
            final String oldSentence = sentence;
            String label = iterator.currentLabel();
            System.out.println("Starting patent: "+label);
            Double[] data = new Double[1+keywords.size()];
            data[0] = isSEP.contains(label) ? 1.0 : 0.0; // SEP status
            for(int i = 0; i < keywords.size(); i++) {
                sentence=oldSentence;
                double score = 0.0;
                String keyword = keywords.get(i);
                while(sentence.contains(keyword)) {
                    score+=1.0;
                    sentence = sentence.replaceFirst(keyword,"");
                }
                data[i+1]=score;
            }
            if(Arrays.stream(data).collect(Collectors.summingDouble(d->d))>=0.5) {
                // no need to add lame data
                patentToDataMap.put(label, data);
            }
        }

        List<String> headers = new ArrayList<>();
        headers.add("asset");
        headers.add("is_sep");
        for(String keyword : keywords) {
            headers.add(keyword);
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        // write headers
        StringJoiner headerLine = new StringJoiner(",","","\n");
        for(String header : headers) {
            headerLine.add(header.toLowerCase().replaceAll(" ","_").replaceAll(",",""));
        }
        bw.write(headerLine.toString());
        // write data
        patentToDataMap.forEach((patent,data)->{
            StringJoiner line = new StringJoiner(",","","\n");
            line.add(patent);
            for(Double value : data) {
                line.add(value.toString());
            }
            synchronized (bw) {
                try {
                    bw.write(line.toString());
                    bw.flush();
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        bw.close();
    }

    public static void main(String[] args) throws IOException,SQLException {
        // run all three models consecutively
        final int keywordLimit = 200;
        final int samplingRatio = 100;
        List<String> patents2G = new ArrayList<>(GetEtsiPatentsList.get2GPatents());
        runCPCModel(new File("ms_2g_cpc_results.csv"),patents2G,samplingRatio);
        List<String> patents3G = new ArrayList<>(GetEtsiPatentsList.get3GPatents());
        runCPCModel(new File("ms_3g_cpc_results.csv"),patents3G,samplingRatio);
        List<String> patents4G = new ArrayList<>(GetEtsiPatentsList.get4GPatents());
        runCPCModel(new File("ms_4g_cpc_results.csv"),patents4G,samplingRatio);//runKeywordModel(new File("ms_sep_2g_results.csv"), new File("ms_sep_2g_keywords.csv"),patents2G,samplingRatio,keywordLimit);
        /*List<String> patents3G = new ArrayList<>(GetEtsiPatentsList.get3GPatents());
        runKeywordModel(new File("ms_sep_3g_results.csv"), new File("ms_sep_3g_keywords.csv"),patents3G,samplingRatio,keywordLimit);
        List<String> patents4G = new ArrayList<>(GetEtsiPatentsList.get4GPatents());
        runKeywordModel(new File("ms_sep_4g_results.csv"), new File("ms_sep_4g_keywords.csv"),patents4G,samplingRatio,keywordLimit);
        */
    }
}
