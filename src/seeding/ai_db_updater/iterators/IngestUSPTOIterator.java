package seeding.ai_db_updater.iterators;

import lombok.Getter;
import seeding.ai_db_updater.iterators.url_creators.UrlCreator;

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.RecursiveAction;


/**
 * Created by Evan on 7/5/2017.
 */
public abstract class IngestUSPTOIterator implements DateIterator {
    private UrlCreator[] urlCreators;
    @Getter
    private String zipFilePrefix;

    public IngestUSPTOIterator(String zipFilePrefix, UrlCreator... urlCreators) {
        this.zipFilePrefix=zipFilePrefix;
        this.urlCreators=urlCreators;
    }

    @Override
    public void run(LocalDate startDate, Collection<LocalDate> failedDates) {
        List<RecursiveAction> tasks = new ArrayList<>();
        List<LocalDate> failedDatesList = new ArrayList<>(failedDates);
        while (startDate.isBefore(LocalDate.now())||failedDatesList.size()>0) {
            if(failedDatesList.size()>0) {
                final LocalDate date = failedDatesList.remove(0);
                final String zipFilename = zipFilePrefix + date.format(DateTimeFormatter.ISO_DATE);
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
                                System.out.println("FOUND!!!!!!!!!!!!");

                            } catch (Exception e) {
                                System.out.println("... Failed");
                            }
                        }
                    }
                };
                action.fork();
                tasks.add(action);
            }
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
                                System.out.println("FOUND!!!!!!!!!!!!");

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
