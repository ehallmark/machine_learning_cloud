package ai_db_updater.iterators;

import ai_db_updater.handlers.CustomHandler;
import net.lingala.zip4j.core.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Evan on 7/6/2017.
 */
public class IngestUSPTOAssignmentIterator {
    private String zipFilePrefix;

    public IngestUSPTOAssignmentIterator(String zipFilePrefix) {
        this.zipFilePrefix=zipFilePrefix;
    }

    public void run() {
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
        for(int i = backYearDataStartNum; i < backYearDataStartNum + numFilesForBackYearData; i++) {
            backYearDates.add(String.format("%06d", backYearDataDate)+"-"+String.format("%02d", i));
        }

        int lastIngestedDate = startDateNum;
        System.out.println("Starting with date: " + lastIngestedDate);
        System.out.println("Ending with date: " + endDateInt);
        String base_url = "http://patents.reedtech.com/downloads/PatentAssignmentText/---/ad20";
        while (lastIngestedDate <= endDateInt||backYearDates.size()>0) {
            String finalUrlString;
            if (backYearDates.isEmpty()) {
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
            } else {
                finalUrlString = base_url + backYearDates.remove(0) + ".zip";
                finalUrlString = finalUrlString.replaceFirst("---", "1980-2015");
            }
            try {
                // Unzip file
                URL website = new URL(finalUrlString);
                System.out.println("Trying: " + website.toString());
                ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                FileOutputStream fos = new FileOutputStream(zipFilePrefix+lastIngestedDate);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                fos.close();

            } catch (Exception e) {
                System.out.println("Unable to get file");
                continue;
            }
        }
    }
}
