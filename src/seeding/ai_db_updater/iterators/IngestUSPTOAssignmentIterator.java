package seeding.ai_db_updater.iterators;

import lombok.Getter;
import org.eclipse.collections.impl.bag.strategy.mutable.HashBagWithHashingStrategy;
import seeding.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Created by Evan on 7/6/2017.
 */
public class IngestUSPTOAssignmentIterator implements DateIterator {
    @Getter
    private String zipFilePrefix = Constants.ASSIGNMENT_ZIP_FOLDER;

    public void run(LocalDate startDate, Collection<LocalDate> failedDates) {
        List<LocalDate> failedDatesList = new ArrayList<>(failedDates);
        // go through assignment xml data and update records using assignment sax handler
        LocalDate date = LocalDate.now();
        String endDateStr = String.valueOf(date.getYear()).substring(2, 4) + String.format("%02d", date.getMonthValue()) + String.format("%02d", date.getDayOfMonth());
        Integer endDateInt = Integer.valueOf(endDateStr);

        // INITIAL OPTIONS TO SET
        final int backYearDataDate = 151231;
        final int numFilesForBackYearData = 14;
        final int backYearDataStartNum = 1;
        final int startDateNum = 160101;
        List<String> backYearDates = new ArrayList<>(numFilesForBackYearData);

        if(startDate.getYear()< 2016) {
            for (int i = backYearDataStartNum; i < backYearDataStartNum + numFilesForBackYearData; i++) {
                backYearDates.add(String.format("%06d", backYearDataDate) + "-" + String.format("%02d", i));
            }
        }

        int lastIngestedDate = startDateNum;
        System.out.println("Starting with date: " + lastIngestedDate);
        System.out.println("Ending with date: " + endDateInt);
        String base_url = "http://patents.reedtech.com/downloads/PatentAssignmentText/---/ad20";
        while (lastIngestedDate <= endDateInt||backYearDates.size()>0||failedDatesList.size()>0) {
            String finalUrlString;
            String zipDate;
            boolean wasFailed = false;
            if(failedDatesList.size()>0) {
                wasFailed= true;
                int failedDate = Integer.valueOf(failedDatesList.remove(0).format(DateTimeFormatter.BASIC_ISO_DATE)) % 20000000;
                finalUrlString = base_url + String.format("%06d", failedDate) + ".zip";
                finalUrlString = finalUrlString.replace("---", "20" + String.format("%02d", failedDate / 10000));
                zipDate=String.valueOf(failedDate);
            } else if (backYearDates.isEmpty()) {
                lastIngestedDate = lastIngestedDate + 1;
                // don't over search days
                if (lastIngestedDate % 100 > 31) {
                    lastIngestedDate = lastIngestedDate + 100 - (lastIngestedDate % 100);
                }
                if (lastIngestedDate % 10000 > 1231) {
                    lastIngestedDate = lastIngestedDate + 10000 - (lastIngestedDate % 10000);
                }
                finalUrlString = base_url + String.format("%06d", lastIngestedDate) + ".zip";
                finalUrlString = finalUrlString.replace("---", "20" + String.format("%02d", lastIngestedDate / 10000));
                zipDate=String.valueOf(lastIngestedDate);
            } else {
                zipDate = backYearDates.remove(0);
                finalUrlString = base_url + zipDate + ".zip";
                finalUrlString = finalUrlString.replaceFirst("---", "1980-2015");

            }
            if(wasFailed || ! new File(zipFilePrefix+zipDate).exists()) {
                try {
                    // Unzip file
                    URL website = new URL(finalUrlString);
                    System.out.println("Trying: " + website.toString());
                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(zipFilePrefix + zipDate);
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    System.out.println("FOUND!!!!!!!!!!!!");

                } catch (Exception e) {
                    System.out.println("Unable to get file");
                    continue;
                }
            }
        }
    }

}
