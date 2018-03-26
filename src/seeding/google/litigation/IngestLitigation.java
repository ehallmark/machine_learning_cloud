package seeding.google.litigation;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import seeding.google.mongo.IngestJsonHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class IngestLitigation {

    private static String anyMatch(String c, Collection<String> collection) {
        return collection.stream().filter(p->c.contains(p)).findFirst().orElse(null);
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
                        map.put("plaintiff",plaintiff);
                        map.put("defendant",defendant);
                        String pla = anyMatch(plaintiff,companies);
                        if(pla!=null) {
                            casesAsPlaintiff.putIfAbsent(pla, new ArrayList<>());
                            casesAsPlaintiff.get(pla).add(map);
                            System.out.println("Plaintiff: "+plaintiff);
                            System.out.println("Defendant: "+defendant);
                            System.out.println("Date: "+map.get("date_filed"));

                        }
                        String def = anyMatch(defendant,companies);
                        if(def!=null) {
                            casesAsDefendant.putIfAbsent(def, new ArrayList<>());
                            casesAsDefendant.get(def).add(map);
                            System.out.println("Plaintiff: "+plaintiff);
                            System.out.println("Defendant: "+defendant);
                            System.out.println("Date: "+map.get("date_filed"));

                        }
                    }
                }
            }
        });

        GzipCompressorInputStream gzip2 = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(pacerCaseFile)));
        IngestJsonHelper.streamJsonFile(gzip2, Collections.emptyList()).forEach(map->{
            if(map.containsKey("date_filed") && map.get("date_filed").toString().length()>1 && map.containsKey("case_number")) {
                if(map.containsKey("case_name")) {
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
                            String pla = anyMatch(plaintiff,companies);
                            if(pla!=null) {
                                pacerCasesAsPlaintiff.putIfAbsent(pla, new ArrayList<>());
                                pacerCasesAsPlaintiff.get(pla).add(map);
                                System.out.println("Plaintiff: "+plaintiff);
                                System.out.println("Defendant: "+defendant);
                                System.out.println("Date: "+map.get("date_filed"));

                            }
                            String def = anyMatch(defendant,companies);
                            if(def!=null) {
                                pacerCasesAsDefendant.putIfAbsent(def, new ArrayList<>());
                                pacerCasesAsDefendant.get(def).add(map);
                                System.out.println("Plaintiff: "+plaintiff);
                                System.out.println("Defendant: "+defendant);
                                System.out.println("Date: "+map.get("date_filed"));

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
