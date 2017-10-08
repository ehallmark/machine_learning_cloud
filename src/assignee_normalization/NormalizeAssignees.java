package assignee_normalization;

import seeding.Database;
import util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/8/2017.
 */
public class NormalizeAssignees {
    public static void main(String[] args) {
        List<String> allAssignees = Collections.synchronizedList(new ArrayList<>(Database.getAssignees()));


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

         List<Pair<String,Integer>> wordCountList = wordCountMap.entrySet().parallelStream().sorted((e1,e2)->e2.getValue().compareTo(e1.getValue())).map(e->new Pair<>(e.getKey(),e.getValue())).collect(Collectors.toList());

         wordCountList.stream().limit(100).forEach(pair->{
             System.out.println("word "+pair._1+": "+pair._2);
         });

         Set<String> stopWords = new HashSet<>();
         stopWords.add("OF");
         stopWords.add("THE");
         stopWords.add("AND");

        AtomicInteger numChanges = new AtomicInteger(0);
        Map<String,String> originalToNormalizedAssigneeMap = allAssignees.parallelStream().collect(Collectors.toMap(k->k,assignee->{
            String[] words = assignee.split("\\s+");
            StringJoiner sj = new StringJoiner(" ");
            sj.add(words[0]);
            for(int i = 1; i < words.length; i++) {
                String word = words[i];
                double maxScore = ((double)MAX_DOC_FREQUENCY);
                double wordScore = Math.log(Math.E+i) * wordCountMap.get(word) / Math.log(Math.E+words.length) ;
                //System.out.println("score for word: "+word+" = "+wordScore);
                if(stopWords.contains(word) || maxScore > wordScore) {
                    sj.add(word);
                } else {
                    break;
                }
            }
            String normalized = sj.toString();
            if(!assignee.equals(normalized)) {
                System.out.println(numChanges.getAndIncrement());
                System.out.println(assignee + ": " + sj.toString());
            }
            return sj.toString();
        }));

        System.out.println("Total num changes: "+numChanges.get());
    }
}
