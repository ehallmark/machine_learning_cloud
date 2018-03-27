package scrape_patexia;

import java.io.*;
import java.net.URL;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

public class Scraper {

    public static void main(String[] args) throws Exception {
        System.setProperty("jsse.enableSNIExtension", "false"); // VERY IMPORTANT!!!

        //final String username = "Evan Hallmark";
        //final String password = "Evan1234";


        LocalDate start = LocalDate.now();
        File dir = new File("patexia_dump");
        if(!dir.exists()) dir.mkdirs();

        while(!start.isBefore(LocalDate.of(2000,1,1))) {
            File outputFile = new File(dir,"patexia-scrape-"+start.toString()+".html");
            if(!outputFile.getParentFile().exists()) {
                outputFile.getParentFile().mkdir();
            }

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

                    //TimeUnit.MILLISECONDS.sleep(10);
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
