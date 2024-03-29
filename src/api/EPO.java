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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class EPO {

    public static Map<String, Object> getEpoData(String asset, boolean includeSelf) throws Exception {
        String docDbAsset;
        if (asset.length()==13) {
            docDbAsset = asset.substring(0, 6) + asset.substring(7);
        } else {
            docDbAsset = asset;
        }
        final int retries = 3;
        String familyData = null;
        for (int i = 0; i < retries; i++) {
            try {
                familyData = new ScrapeEPO().getFamilyMembersForAssetHelper(docDbAsset, ScrapeEPO.generateNewAuthToken(), null);
                break;
            } catch (Exception e) {
                e.printStackTrace();
                TimeUnit.SECONDS.sleep(1+i);
            }
        }

        if (familyData == null) {
            return null;
        }

        Document document = Jsoup.parse(familyData);
        //System.out.println("Doc: "+document.html());
        Elements family = document.select("ops|family-member");
        Map<String, Map<String, Object>> familyMembersMap = new HashMap<>(family.size());
        String familyId = null;
        if (family.size() > 0) {
            familyId = family.get(0).attr("family-id");
        }
        if (family.size()>1) {
            for (Element member : family) {
                Element pubRefNode = member.select("publication-reference").first();
                Element appRefNode = member.select("application-reference").first();
                if (pubRefNode != null && appRefNode != null) {
                    String appRef = appRefNode.attr("doc-id");
                    if (appRef != null) {
                        String number = pubRefNode.select("doc-number").first().text().trim();
                        String country = pubRefNode.select("country").first().text().trim();
                        String kind = pubRefNode.select("kind").first().text().trim();
                        String date = pubRefNode.select("date").first().text().trim();
                        System.out.println("App ref for "+country+number+": "+appRef);
                        if (number.length() == 10) {
                            // fix docdb format
                            number = number.substring(0, 4) + "0" + number.substring(4);
                        }
                        date = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8);

                        Map<String, Object> result = new HashMap<>();
                        result.put("number", number);
                        result.put("country", country);
                        result.put("kind", kind);
                        result.put("date", date);
                        if (familyMembersMap.containsKey(appRef)) {
                            Map<String, Object> existing = familyMembersMap.get(appRef);
                            if (date.compareTo(existing.get("date").toString()) > 0) {
                                familyMembersMap.put(appRef, result);
                            }
                        } else {
                            familyMembersMap.put(appRef, result);
                        }
                    }
                }


            }

        }
        List<Map<String, Object>> familyMembers = familyMembersMap.values().stream().filter(p->{
            return includeSelf || !asset.equals((String)p.get("country")+p.get("number"));
        }).collect(Collectors.toList());
        Map<String, Object> data = new HashMap<>();
        data.put("family_members", familyMembers);
        data.put("family_id", familyId);
        return data;
    }


    public static void main(String[] args) throws Exception {
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(getEpoData("US2021250327", true)));
        //System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(getEpoData("US9781219", true)));
    }
}
