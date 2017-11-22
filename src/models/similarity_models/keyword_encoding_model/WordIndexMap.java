package models.similarity_models.keyword_encoding_model;

import cpc_normalization.CPCHierarchy;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 11/9/17.
 */
public class WordIndexMap {
    private static final String WORD_TO_INDEX_FILENAME = Constants.DATA_FOLDER+"keyword_encoding_word_to_idx_map.jobj";
    private static Map<String,Integer> cache;
    public static synchronized Map<String,Integer> loadOrCreateWordIdxMap(Set<String> words) {
        if (cache==null) {
            // try loading from file
            try {
                cache = (Map<String,Integer>)Database.tryLoadObject(new File(WORD_TO_INDEX_FILENAME));
            } catch(Exception e) {
                cache = null;
            }
            if(cache==null) {
                AtomicInteger idx = new AtomicInteger(0);
                System.out.println("Could not find cpc idx map... creating new one now.");
                cache = words.stream().sorted().sequential().collect(Collectors.toMap(word -> word, e -> idx.getAndIncrement()));
                System.out.println("Input size: " + cache.size());
                System.out.println("Saving cpc idx map...");
                Database.trySaveObject(cache,new File(WORD_TO_INDEX_FILENAME));
            }
        }
        return cache;
    }
}
