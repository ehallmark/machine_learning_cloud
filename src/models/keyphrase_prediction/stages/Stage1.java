package models.keyphrase_prediction.stages;


import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.Model;
import seeding.Database;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 9/12/17.
 */
public class Stage1 extends Stage<Map<MultiStem,AtomicLong>> {
    private static final boolean debug = false;
    private int minDocFrequency;
    private double maxDocFrequencyRatio;
    private  Map<String,Map<String,AtomicInteger>> phraseCountMap;
    private AtomicLong totalDocumentsSeen = new AtomicLong(0);
    public Stage1(Model model) {
        super(model);
        phraseCountMap = Collections.synchronizedMap(new HashMap<>());
        this.minDocFrequency=model.getMinDocFrequency();
        this.maxDocFrequencyRatio=model.getMaxDocFrequencyRatio();
    }

    @Override
    public Map<MultiStem,AtomicLong> run(boolean alwaysRerun) {
        if(alwaysRerun || !getFile().exists() || !getTransformedDataFolder().exists()) {
            data = buildVocabularyCounts();
            data = truncateBetweenLengths();
            Database.trySaveObject(data, getFile());

        } else {
            try {
                loadData();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return data;
    }

    private Map<MultiStem,AtomicLong> truncateBetweenLengths() {
        return data.entrySet().parallelStream()
                .filter(e->e.getValue().get()>=minDocFrequency)
                .filter(e->((double)e.getValue().get())/totalDocumentsSeen.get()<=maxDocFrequencyRatio)
                .collect(Collectors.toMap(e->e.getKey(), e->e.getValue()));
    }

    private Map<MultiStem,AtomicLong> buildVocabularyCounts() {
        return this.buildVocabularyCounts(function->{
            collectVocabAndTransformData(function,getDefaultAnnotator((stem,label,map)->{
                checkStem(stem,label,map,phraseCountMap);
                return null;
            }));
            return null;
        },attr->null);
    }

    public Map<MultiStem, AtomicLong> buildVocabularyCounts(Function<Function<Map<String,Object>,Void>,Void> iteratorFunction, Function<Map<String,Object>,Void> andThen) {
        data = Collections.synchronizedMap(new HashMap<>());
        Function<Map<String,Object>,Map<String,Object>> attributesFunction = attributes -> {
            Collection<MultiStem> appeared = (Collection<MultiStem>)attributes.get(APPEARED);
            totalDocumentsSeen.getAndIncrement();
            appeared.forEach(stem->{
                AtomicLong docCnt = data.get(stem);
                if(docCnt==null) {
                    data.put(stem,new AtomicLong(1));
                } else {
                    docCnt.getAndIncrement();
                }
            });
            return attributes;
        };

        iteratorFunction.apply(attributesFunction.andThen(andThen));

        System.out.println("Starting to find best phrases for each stemmed phrase.");
        new ArrayList<>(data.keySet()).parallelStream().forEach(stem->{
            String stemStr = stem.toString();
            if(phraseCountMap.containsKey(stemStr)) {
                // extract most common representation of the stem
                String bestPhrase = phraseCountMap.get(stemStr).entrySet().stream().sorted((e1,e2)->Integer.compare(e2.getValue().get(),e1.getValue().get())).map(e->e.getKey()).findFirst().orElse(null);
                if(bestPhrase!=null) {
                    stem.setBestPhrase(bestPhrase);
                } else {
                    data.remove(stem);
                }
                if(debug) System.out.println("Best phrase for "+stemStr+": "+bestPhrase);
            } else {
                data.remove(stem);
            }
        });
        return data;
    }


    private static void checkStem(String[] stems, String label, Map<MultiStem,AtomicInteger> appeared, Map<String,Map<String,AtomicInteger>> phraseCountMap) {
        MultiStem multiStem = new MultiStem(stems,-1);
        String stemPhrase = multiStem.toString();
        phraseCountMap.putIfAbsent(stemPhrase,Collections.synchronizedMap(new HashMap<>()));
        Map<String,AtomicInteger> innerMap = phraseCountMap.get(stemPhrase);
        innerMap.putIfAbsent(label,new AtomicInteger(0));
        innerMap.get(label).getAndIncrement();
        appeared.putIfAbsent(multiStem,new AtomicInteger(0));
        appeared.get(multiStem).getAndIncrement();
    }


}
