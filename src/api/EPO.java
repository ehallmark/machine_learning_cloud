package api;

import com.google.gson.GsonBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nd4j.linalg.primitives.Pair;
import seeding.google.postgres.epo.ScrapeEPO;

import java.io.IOException;
import java.util.*;

public class EPO {

    public static Map<String, Object> getEpoData(String asset) throws Exception {
        String familyData = new ScrapeEPO().getFamilyMembersForAssetHelper(asset, ScrapeEPO.generateNewAuthToken(), null);

        Document document = Jsoup.parse(familyData);
        Elements family = document.select("ops|family-member");
        List<Map<String, Object>> familyMembers = new ArrayList<>(family.size());

        if (family.size()>1) {
            for (Element member : family) {
                Element pubRefNode = member.select("publication-reference").first();
                if (pubRefNode != null) {
                    String number = pubRefNode.select("doc-number").first().text().trim();
                    String country = pubRefNode.select("country").first().text().trim();
                    String kind = pubRefNode.select("kind").first().text().trim();
                    String date = pubRefNode.select("date").first().text().trim();
                    if(number.length()==10) {
                        // fix docdb format
                        number = number.substring(0,4)+"0"+number.substring(4);
                    }
                    date = date.substring(0, 4)+"-"+date.substring(4,6)+"-"+date.substring(6,8);

                    Map<String, Object> result = new HashMap<>();
                    result.put("number", number);
                    result.put("country", country);
                    result.put("kind", kind);
                    result.put("date", date);
                    familyMembers.add(result);
                }


            }

        }
        return Collections.singletonMap("family_members", familyMembers);
    }


    public static void main(String[] args) throws Exception {
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(getEpoData("US2010015477")));
    }
}
