package seeding;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.util.*;

/**
 * Created by ehallmark on 10/5/16.
 */
public class ParseHTML {
    public static List<String> extractHTML(File htmlFile) throws Exception {
        Document doc = Jsoup.parse(htmlFile, Charset.defaultCharset().name());
        // get all company data
        Set<String> companies = new HashSet<>();
        Elements all = doc.getAllElements();
        for (Element elem : all) {
            Document line = Jsoup.parse(elem.text());
            for(Element tag : line.select("a.prf-tooltip")) {
                if (tag.text().replaceAll("[^A-Za-z0-9]", "").trim().isEmpty()) continue;
                if(tag.attr("href")==null||!tag.attr("href").contains("c=")) continue;
                companies.add(tag.text().trim());
            }

        }

        return new ArrayList<>(companies);
    }

    public static void main(String[] args) throws Exception {
        List<String> tags = Arrays.asList("Virtual Reality", "Augmented Reality", "Speech Recognition", "Object Recognition","Artificial Intelligence","Deep Learning","Machine Learning", "Neural Networks", "Natural Language Processing", "Intelligent Systems");
        List<String> abreviations = Arrays.asList("vr","ar","sr","or","ai","dl","ml","nn","nlp","is");
        for(int i = 0; i < tags.size(); i++) {
            String tag = tags.get(i);
            extractHTML(new File("pitchbook_adobe_results_"+abreviations.get(i)+".html")).forEach(company -> {
                System.out.println(company + "\t" + tag);
            });

        }
    }
}
