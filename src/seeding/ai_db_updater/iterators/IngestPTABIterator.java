package seeding.ai_db_updater.iterators;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import seeding.Constants;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RecursiveAction;


/**
 * Created by Evan on 7/5/2017.
 */
public class IngestPTABIterator implements DateIterator {
    @Getter
    private String zipFilePrefix = Constants.PTAB_ZIP_FOLDER;

    public IngestPTABIterator() {
    }

    @Override
    public void run(LocalDate startDate, Collection<LocalDate> failedDates) {
        // get ptab urls from https://patents.reedtech.com/pgptab.php
        List<String> filenames = Collections.synchronizedList(new ArrayList<>());
        List<URL> urls = Collections.synchronizedList(new ArrayList<>());
        try {
            Document ptabWebsite = Jsoup.connect("https://patents.reedtech.com/pgptab.php").get();
            Elements links = ptabWebsite.select(".bulktable td a[href]");
            for(Element link : links) {
                if(link.text().startsWith("PTAB_")) {
                    // valid
                    System.out.println("Found link: "+link.text());
                    filenames.add(link.text());
                    urls.add(new URL("http://patentscur.reedtech.com/"+link.attr("href")));
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            System.exit(1);
        }


        for(int i = 0; i < urls.size(); i++) {
            String filename = filenames.get(i);
            final String zipFilename = zipFilePrefix + filename;
            if (!new File(zipFilename).exists()) {
                try {
                    URL website = urls.get(i);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(zipFilename);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    System.out.println("FOUND!!!!!!!!!!!!");

                } catch (Exception e) {
                    System.out.println("... Failed");
                }
            }
        }

    }

}
