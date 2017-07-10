package ai_db_updater.iterators;

import ai_db_updater.handlers.CustomHandler;
import ai_db_updater.iterators.url_creators.UrlCreator;
import ai_db_updater.tools.ZipHelper;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RecursiveAction;


/**
 * Created by Evan on 7/5/2017.
 */
public class IngestUSPTOIterator {
    public LocalDate startDate;
    private UrlCreator[] urlCreators;
    private String zipFilePrefix;

    public IngestUSPTOIterator(LocalDate startDate, String zipFilePrefix, UrlCreator... urlCreators) {
        this.startDate=startDate;
        this.zipFilePrefix=zipFilePrefix;
        this.urlCreators=urlCreators;
    }

    public void run() {
        List<RecursiveAction> tasks = new ArrayList<>();
        while (startDate.isBefore(LocalDate.now())) {
            final String zipFilename = zipFilePrefix + startDate;
            if(!new File(zipFilename).exists()) {
                final LocalDate date = startDate;
                RecursiveAction action = new RecursiveAction() {
                    @Override
                    protected void compute() {
                        for (UrlCreator urlCreator : urlCreators) {
                            try {
                                URL website = new URL(urlCreator.create(date));
                                System.out.println("Trying: " + website.toString());
                                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                                FileOutputStream fos = new FileOutputStream(zipFilename);
                                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                                fos.close();

                            } catch (Exception e) {
                                System.out.println("... Failed");
                            }
                        }
                    }
                };
                action.fork();
                tasks.add(action);
            }
            startDate = startDate.plusDays(1);
            while(tasks.size()>Runtime.getRuntime().availableProcessors()*4) {
                tasks.remove(0).join();
            }
        }

        while(!tasks.isEmpty()) {
            tasks.remove(0).join();
        }

    }

}
