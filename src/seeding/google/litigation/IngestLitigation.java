package seeding.google.litigation;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import seeding.google.mongo.IngestJsonHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class IngestLitigation {

    private static boolean anyMatch(String c, Collection<String> collection) {
        return collection.stream().anyMatch(p->c.contains(p));
    }

    public static void main(String[] args) throws Exception {
        List<String> companies = Arrays.asList(
                "apple",
                "google",
                "amazon",
                "netflix",
                "facebook",
                "at&t",
                "directv",
                "time warner cable",
                "cox communications",
                "verizon",
                "comcast",
                "universal pictures",
                "disney"
        );


        File caseFile = new File("/usb2/data/google-big-query/cases/").listFiles()[0];
        File pacerCaseFile = new File("/usb2/data/google-big-query/pacer-cases/").listFiles()[0];
        Map<String,List<Map<String,Object>>> pacerCasesAsDefendant = new HashMap<>();
        Map<String,List<Map<String,Object>>> pacerCasesAsPlaintiff = new HashMap<>();
        Map<String,List<Map<String,Object>>> casesAsDefendant = new HashMap<>();
        Map<String,List<Map<String,Object>>> casesAsPlaintiff = new HashMap<>();

        GzipCompressorInputStream gzip = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(caseFile)));
        IngestJsonHelper.streamJsonFile(gzip, Collections.emptyList()).forEach(map->{
            if(map.containsKey("case_number") && map.containsKey("case_cause")&& ((String)map.get("case_cause")).toLowerCase().contains("patent infringement")) {
                String involved = (String)map.get("case_name");
                if(involved!=null) {
                    String[] parties = involved.toLowerCase().split("v\\.");
                    if(parties.length==2) {
                        String plaintiff = parties[0].trim();
                        String defendant = parties[1].trim();
                        System.out.println("Plaintiff: "+plaintiff);
                        System.out.println("Defendant: "+defendant);
                        System.out.println("Date: "+map.get("date_filed"));
                        map.put("plaintiff",plaintiff);
                        map.put("defendant",defendant);
                        if(anyMatch(plaintiff,companies)) {
                            casesAsPlaintiff.putIfAbsent(plaintiff, new ArrayList<>());
                            casesAsPlaintiff.get(plaintiff).add(map);
                        }
                        if(anyMatch(defendant,companies)) {
                            casesAsDefendant.putIfAbsent(defendant, new ArrayList<>());
                            casesAsDefendant.get(defendant).add(map);
                        }
                    }
                }
            }
        });

        GzipCompressorInputStream gzip2 = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(pacerCaseFile)));
        IngestJsonHelper.streamJsonFile(gzip2, Collections.emptyList()).forEach(map->{
            if(map.containsKey("date_filed") && map.get("date_filed").toString().length()>1 && map.containsKey("case_number")) {
                if(map.containsKey("case_name")&&anyMatch(map.get("case_name").toString().toLowerCase(),companies)) {
                    String involved = (String)map.get("case_name");
                    if(involved!=null) {
                        String[] parties = involved.toLowerCase().split("v\\.");
                        if(parties.length==2) {
                            String plaintiff = parties[0].trim();
                            String defendant = parties[1].trim();
                            System.out.println("Plaintiff: "+plaintiff);
                            System.out.println("Defendant: "+defendant);
                            System.out.println("Date: "+map.get("date_filed"));
                            map.put("plaintiff",plaintiff);
                            map.put("defendant",defendant);
                            if(anyMatch(plaintiff,companies)) {
                                pacerCasesAsPlaintiff.putIfAbsent(plaintiff, new ArrayList<>());
                                pacerCasesAsPlaintiff.get(plaintiff).add(map);
                            }
                            if(anyMatch(defendant,companies)) {
                                pacerCasesAsDefendant.putIfAbsent(defendant, new ArrayList<>());
                                pacerCasesAsDefendant.get(defendant).add(map);
                            }
                        }
                    }
                }
            }
        });

        System.out.println("Total matched normal cases plaintiff: "+casesAsPlaintiff.size());
        System.out.println("Total matched normal cases defendant: "+casesAsDefendant.size());
        System.out.println("Total matched pacer cases plaintiff: "+pacerCasesAsPlaintiff.size());
        System.out.println("Total matched pacer cases defendant: "+pacerCasesAsDefendant.size());

        gzip.close();
    }
}
