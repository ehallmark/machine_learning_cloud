package models.dl4j_neural_nets.tools;

import models.dl4j_neural_nets.iterators.sequences.DatabaseIteratorFactory;
import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.models.sequencevectors.interfaces.SequenceIterator;
import org.deeplearning4j.models.sequencevectors.sequence.Sequence;
import org.deeplearning4j.models.word2vec.VocabWord;
import tools.Emailer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 1/5/17.
 */
public class GetMostFrequentWords {
    public static void main(String[] args) throws Exception {
        SequenceIterator<VocabWord> iterator = DatabaseIteratorFactory.SamplePatentSequenceIterator();
        Map<String,AtomicInteger> wordFrequencyMap = new HashMap<>();
        while(iterator.hasMoreSequences()) {
            Sequence<VocabWord> sequence = iterator.nextSequence();
            sequence.getElements().forEach(elem->{
                if(wordFrequencyMap.containsKey(elem.getWord())) {
                    wordFrequencyMap.get(elem.getWord()).getAndIncrement();
                } else {
                    wordFrequencyMap.put(elem.getWord(),new AtomicInteger(1));
                }
            });
        }

        List<String> topWords = wordFrequencyMap.entrySet().stream().map(e->{
            return new Pair<>(e.getKey(),e.getValue());
        }).sorted((o1,o2)->Integer.compare(o2.getSecond().get(),o1.getSecond().get()))
                .limit(Math.round(0.2f*wordFrequencyMap.size()))
                .map(pair->pair.getFirst()).collect(Collectors.toList());

        System.out.println("Top 10% Frequent Words:\n"+String.join(" ",topWords));
        StringJoiner sj = new StringJoiner("\",\"","new String[]{\"","\"};");
        for(String topWord : topWords) {
            sj.add(topWord);
        }

        new Emailer(sj.toString());
    }
}
