package scrape_patexia;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Scraper {

    private static LocalDate getLastSeenDateFromExistingFiles(File[] files) {
        return Stream.of(files).map(file->{
            String dateStr = file.getName().replace("patexia-scrape-","").replace(".html","");
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
        }).sorted(Comparator.reverseOrder()).limit(1).findFirst().orElse(null);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("jsse.enableSNIExtension", "false"); // VERY IMPORTANT!!!

        LocalDate start = LocalDate.now();
        File dir = new File("patexia_dump");
        if(!dir.exists()) dir.mkdirs();

        LocalDate earliestDateToScrape = null;
        File[] alreadyScrapedFiles = dir.listFiles();
        if(alreadyScrapedFiles!=null) {
            int numPaddingDays = 60;
            LocalDate lastSeenDate = getLastSeenDateFromExistingFiles(alreadyScrapedFiles);
            if (lastSeenDate!=null) {
                earliestDateToScrape = lastSeenDate.minusDays(numPaddingDays);
                System.out.println("Starting to scrape from: "+earliestDateToScrape.toString()+" to "+start.toString());
            } else {
                System.out.println("Warning: files exist, but could not extract date...");
                System.exit(1);
            }
        } else {
            earliestDateToScrape = LocalDate.of(1990,1,1);
        }

        while(!start.isBefore(earliestDateToScrape)) {
            File outputFile = new File(dir,"patexia-scrape-"+start.toString()+".html");

            final BufferedWriter outputStream = new BufferedWriter(new FileWriter(outputFile));
            int total = 51;
            AtomicInteger cnt = new AtomicInteger(1);
            final LocalDate date = start;
            boolean foundAny = false;
            for(int i = 1; i <= total; i++) {
                System.out.println(date.toString()+": "+cnt.getAndIncrement() + " / " + (total-1));
                try {
                    String page = scrapePage(i,date);
                    if(page==null||page.trim().isEmpty()||page.contains("We couldn't find that page!")||page.contains("No lawsuits found")) {
                        break;
                    }
                    synchronized (outputStream) {
                        outputStream.write(page+"\n   \n  \n \n\n");
                        outputStream.flush();
                        foundAny=true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            outputStream.close();
            if(!foundAny) {
                System.out.println("None found for "+outputFile.getName());
                outputFile.delete();
            }
            start = start.minusDays(1);
        }
    }

    private static String toDate(LocalDate d) {
        return String.valueOf(d.getYear())+"-"+d.getMonthValue()+"-"+d.getDayOfMonth();
    }

    private static String scrapePage(int page, LocalDate date) throws Exception {
        LocalDate start = date;
        URL url = new URL("https://www.patexia.com/ip-research/lawsuits/page/"+page+"?startFilingDateRange="+toDate(start)+"&endFilingDateRange="+toDate(date));
        return HttpBasicAuth.downloadFileWithAuth(url);
    }
}
