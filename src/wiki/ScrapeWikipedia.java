package wiki;

import models.keyphrase_prediction.MultiStem;
import models.keyphrase_prediction.models.DefaultModel2;
import models.keyphrase_prediction.models.Model;
import models.keyphrase_prediction.stages.Stage1;
import models.keyphrase_prediction.stages.Stage2;
import models.keyphrase_prediction.stages.ValidWordStage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nd4j.linalg.primitives.PairBackup;
import seeding.Constants;
import seeding.Database;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ScrapeWikipedia {
    public static final File wikipediaMapFile = new File(Constants.DATA_FOLDER+"wikipedia_text_from_keywords_map.jobj");
    public static List<String> pullWikipediaPageByPhrase(String[] phrase) {
        try {
            Document document = Jsoup.connect("http://en.wikipedia.org/wiki/" + String.join("_", phrase).toLowerCase()).get();
            if(!document.select("#disambigbox").isEmpty()) {
                //  System.out.println("Is disambiguation: "+String.join("_",phrase));
                return null;
            }
            Elements elements = document.select("#bodyContent div.mw-parser-output").select("p");
            List<String> sentences = new ArrayList<>();
            for (Element element : elements) {
                if (element.hasText()) {
                    sentences.add(element.text().toLowerCase());
                }
            }
            return sentences;
        } catch(Exception e) {
            return null;
        }
    }

    public static boolean isTechnology(String[] phrase) {
        try {
            Document document = Jsoup.connect("http://en.wikipedia.org/wiki/" + String.join("_", phrase).toLowerCase()).get();
            // not disambiguated
            return true;/*
            if(!document.select("#disambigbox").isEmpty()) {
              //  System.out.println("Is disambiguation: "+String.join("_",phrase));
                return false;
            }// else if(!document.select("#contentSub .mw-redirectedfrom").isEmpty()) {
             //   return false;
            //}
            //System.out.println("Size: "+ document.select("#bodyContent div.mw-parser-output").select("p").size());
            return document.select("#bodyContent div.mw-parser-output").select("p").size()>10;
            Elements elements = document.select("#bodyContent div.mw-parser-output").select("p,h1,h2,h3,h4,h5");
            for (Element element : elements) {
                if (element.hasText()) {
                    String text = element.text().toLowerCase();
                    if(text.contains("engineering")||text.contains("science")||text.contains("technology")) {
                        return true;
                    }
                }
            }
            return false;*/
        } catch(Exception e) {
            return false;
        }
    }


    public static Set<MultiStem> filterMultistems(Set<MultiStem> multiStems) {
        Set<MultiStem> technologies = Collections.synchronizedSet(new HashSet<>());
        AtomicInteger cnt = new AtomicInteger(0);
        multiStems.parallelStream().forEach(stem-> {
            boolean isTechnology = isTechnology(stem.getBestPhrase().split(" "));
            if (cnt.getAndIncrement() % 1000 == 999) {
                System.out.println("Seen "+cnt.get()+" out of "+multiStems.size()+" Num Valid: "+technologies.size());
                System.out.println(stem.getBestPhrase() + ": " + isTechnology);
            }
            if(isTechnology) {
                technologies.add(stem);
            }
        });
        System.out.println("Filtered "+multiStems.size()+" multistems down to "+technologies.size());
        return technologies;
    }

    public static Set<MultiStem> loadAllMultistems() {
        // stage 1;
        boolean rerunVocab = false;
        boolean rerunFilters = false;
        boolean filters = true;
        boolean vocab = true;

        Model modelParams = new DefaultModel2();

        Stage1 stage1 = new Stage1(modelParams);
        if(vocab)stage1.run(rerunVocab);
        //if(alwaysRerun)stage1.createVisualization();

        // stage 2
        System.out.println("Pre-grouping data for stage 2...");
        Stage2 stage2 = new Stage2(stage1.get(), modelParams);
        if(filters)stage2.run(rerunFilters);
        //if(alwaysRerun)stage2.createVisualization();

        // valid word
        System.out.println("Pre-grouping data for valid word stage...");
        ValidWordStage validWordStage = new ValidWordStage(stage2.get(), modelParams);
        if(filters)validWordStage.run(rerunFilters);

        return validWordStage.get();
    }

    public static Map<String,List<String>> loadWikipediaMap() {
        return (Map<String,List<String>>) Database.tryLoadObject(wikipediaMapFile);
    }

    public static void main(String[] args) {
        Set<String> phrases = loadAllMultistems().stream().map(s->s.getBestPhrase()).collect(Collectors.toSet());
        System.out.println("Num phrases: "+phrases.size());

        AtomicInteger cnt = new AtomicInteger(0);
        AtomicInteger missing = new AtomicInteger(0);
        AtomicInteger remaining = new AtomicInteger(phrases.size());
        Map<String,List<String>> wikipediaMap = phrases.parallelStream()
                .map(stem->{
                    List<String> sentences = pullWikipediaPageByPhrase(stem.split(" "));
                    PairBackup<String,List<String>> pair;
                    if(sentences==null) {
                        missing.getAndIncrement();
                        pair = null;
                    } else {
                        pair = new PairBackup<>(stem,sentences);
                    }
                    cnt.getAndIncrement();
                    remaining.getAndDecrement();
                    if(cnt.get()%1000==999) {
                        System.out.println("Finished "+cnt.get()+" out of "+phrases.size()+". Missing: "+missing.get()+" out of "+cnt.get()+". Remaining: "+remaining.get());
                        System.out.println("Num found: "+(cnt.get()-missing.get()));
                    }
                    return pair;
                }).filter(p->p!=null)
                .collect(Collectors.toMap(e->e.getFirst(),e->e.getSecond()));

        System.out.println("Saving...");
        Database.trySaveObject(wikipediaMap,wikipediaMapFile);
        System.out.println("Done.");
    }

}
