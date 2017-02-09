package analysis.client_projects;

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

    private static void runModel(File outputFile, File keywordFile, List<String> patents, final int samplingRatio) throws IOException,SQLException {
        // load keywords
        List<String> keywords = loadKeywordFile(keywordFile).stream()
                .map(keyword->keyword.toLowerCase().replaceAll("_"," ").trim())
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
                int nextRand = minPatNum+Math.abs(rand.nextInt())*(maxPatNum-minPatNum);
                System.out.println("Random patent: "+nextRand);
                if(!isSEP.contains(String.valueOf(nextRand))&&Database.hasClassifications(String.valueOf(nextRand))) {
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
        }

        List<String> headers = new ArrayList<>();
        headers.add("asset");
        headers.add("is_sep");
        for(String keyword : keywords) {
            headers.add(keyword);
        }
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
        // write headers
        for(String header : headers) {
            bw.write(header.toLowerCase().replaceAll(" ","_").replaceAll(",",""));
        }
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
        final int samplingRatio = 100;
        List<String> patents2G = new ArrayList<>(GetEtsiPatentsList.get2GPatents());
        runModel(new File("ms_sep_2g_results.csv"), new File("ms_sep_2g_keywords.csv"),patents2G,samplingRatio);
        List<String> patents3G = new ArrayList<>(GetEtsiPatentsList.get3GPatents());
        runModel(new File("ms_sep_3g_results.csv"), new File("ms_sep_3g_keywords.csv"),patents3G,samplingRatio);
        List<String> patents4G = new ArrayList<>(GetEtsiPatentsList.get4GPatents());
        runModel(new File("ms_sep_4g_results.csv"), new File("ms_sep_4g_keywords.csv"),patents4G,samplingRatio);

    }
}
