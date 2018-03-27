package scrape_patexia;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ReadScrapedData {
    public static void main(String[] args) throws Exception {
        File inputDir = new File("patexia_dump");
        AtomicInteger valid = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);

        for(File inputFile : inputDir.listFiles()) {
            String document = FileUtils.readFileToString(inputFile);
            Stream.of(document.split("\n   \n  \n \n\n")).forEach(line -> {
                //System.out.println("lines: "+line.split("<tr").length);
                String[] lines = line.split("<tr");
                for (String l : lines) {
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
        }

        System.out.println("Valid: "+valid.get());
        System.out.println("Total: "+total.get());
    }
}
