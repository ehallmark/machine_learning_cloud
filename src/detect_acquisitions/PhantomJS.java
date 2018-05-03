package detect_acquisitions;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.concurrent.atomic.AtomicInteger;

import static detect_acquisitions.MinHash.fuzzyContains;

public class PhantomJS {
    private static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36";


    public static boolean test(String company1, String company2) {
        company1 = company1.toLowerCase();
        company2 = company2.toLowerCase();

        String search = "\""+company1+"\" \""+company2+"\"".replaceAll("\\s+","+");
        String baseUrl = "https://search.yahoo.com/search?p="+search;

        baseUrl = baseUrl.replace(" ","%20");
        baseUrl = baseUrl.replace("\"","%22");
        baseUrl = baseUrl.replace("&","%26");

        try {
            //System.out.println("URL: "+baseUrl);
            Document doc = Jsoup.connect(baseUrl).get();
            //System.out.println("Doc: "+doc.toString());
            Elements tableRows = doc.select("body div.result");

            AtomicInteger isAcquisition = new AtomicInteger(0);
            for(Element elem : tableRows) {
                String text = elem.text();
                if(text!=null) {
                   // System.out.println(elem.tagName()+": "+text);
                    text = text.toLowerCase();
                    if ((text.contains("acqui")||text.contains("merge")||text.contains("buys"))&&!text.contains("patent")) {
                        if (fuzzyContains(company1, text) && fuzzyContains(company2, text)) {
                            isAcquisition.getAndIncrement();
                            if (isAcquisition.get() > 1) {
                                break;
                            }
                        }
                    }
                }
            }
            return isAcquisition.get()>1;
        }catch(Exception e) {
            System.out.println("Error: ");
            e.printStackTrace();
            return false;
        }
    }


    // test
    public static void main(String[] args) {
        String company1 = "microsoft".toLowerCase();
        String company2 = "linkedin".toLowerCase();
        String company3 = "google".toLowerCase();


        System.out.println("Is microsoft -> linkedin acquisition: "+test(company1,company2));
        System.out.println("Is microsoft -> google acquisition: "+test(company1,company3));

        System.exit(0);
    }
}
