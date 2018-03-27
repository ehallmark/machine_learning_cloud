package scrape_patexia;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReadScrapedData {
    public static void main(String[] args) throws Exception {
        File inputFile = new File("patexia-scrape.html.gz");

        BufferedReader reader = new BufferedReader(new InputStreamReader(new GzipCompressorInputStream(new FileInputStream(inputFile))));

        AtomicInteger valid = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);
        String document = String.join("",reader.lines().collect(Collectors.toList()));
        System.out.println(document);
        Stream.of(document.split("<table")).forEach(line->{
            //System.out.println("lines: "+line.split("<tr").length);
            String[] lines = line.split("<tr");
            for(String l : lines) {
                //System.out.println("Size of line: "+line.length());
                boolean v = false;
                //System.out.println(line);
                if (l.contains("ligitationName")) {
                    System.out.println(l);
                    v = true;
                }
                if (v) valid.getAndIncrement();
                total.getAndIncrement();
            }
        });

        System.out.println("Valid: "+valid.get());
        System.out.println("Total: "+total.get());
    }
}
