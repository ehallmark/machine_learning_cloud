package wiki;

import models.keyphrase_prediction.MultiStem;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ScrapeWikipedia {

    public static List<String> pullWikipediaPageByPhrase(String[] phrase) {
        try {
            Document document = Jsoup.connect("http://en.wikipedia.org/wiki/" + String.join("_", phrase).toLowerCase()).get();
            Elements elements = document.select("#bodyContent div.mw-parser-output").select("p,h1,h2,h3,h4,h5");
            List<String> sentences = new ArrayList<>();
            for (Element element : elements) {
                if (element.hasText()) {
                    sentences.add(element.text());
                }
            }
            return sentences;
        } catch(Exception e) {
            return Collections.emptyList();
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

    public static void main(String[] args) {
        //KeyphrasePredictionPipelineManager predictionPipelineManager = new KeyphrasePredictionPipelineManager(new WordCPC2VecPipelineManager(WordCPC2VecPipelineManager.DEEP_MODEL_NAME,-1,-1,-1));
        //predictionPipelineManager.runPipeline(false,false,false,false,-1,false);
        //Set<MultiStem> multiStems = new HashSet<>(predictionPipelineManager.getMultiStemSet());

        Set<MultiStem> test = new HashSet<>();
        MultiStem s1 = new MultiStem(new String[]{"virtual","reality"},-1);
        MultiStem s2 = new MultiStem(new String[]{"papaya"},-1);
        MultiStem s3 = new MultiStem(new String[]{"notreallyathing"},-1);
        MultiStem s4 = new MultiStem(new String[]{"mercury"},-1);
        s1.setBestPhrase("virtual reality");
        s2.setBestPhrase("papaya");
        s3.setBestPhrase("notreallyathing");
        s4.setBestPhrase("mercury");
        test.addAll(Arrays.asList(s1,s2,s3,s4));


        System.out.println("Starting to filter multistems... Size: "+test.size());

        Set<MultiStem> technologies = filterMultistems(test);

        System.out.println("Technologies size: "+technologies.size());
    }

}
