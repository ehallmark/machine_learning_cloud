package seeding.google.litigation;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import seeding.google.mongo.IngestJsonHelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class IngestLitigation {

    public static void main(String[] args) throws Exception {
        File caseFile = new File("/usb2/data/google-big-query/cases/").listFiles()[0];
        File pacerCaseFile = new File("/usb2/data/google-big-query/pacer-cases/").listFiles()[0];
        Map<String,Map<String,Object>> pacerCases = new HashMap<>();
        Map<String,Map<String,Object>> cases = new HashMap<>();
        GzipCompressorInputStream gzip = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(caseFile)));
        IngestJsonHelper.streamJsonFile(gzip, Collections.emptyList()).forEach(map->{
            if(map.containsKey("case_number") && map.containsKey("case_cause")&& ((String)map.get("case_cause")).toLowerCase().contains("patent infringement")) {
                cases.put((String)map.get("case_number"),map);
            }
        });

        GzipCompressorInputStream gzip2 = new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(pacerCaseFile)));
        IngestJsonHelper.streamJsonFile(gzip2, Collections.emptyList()).forEach(map->{
            if(map.containsKey("date_filed")&& map.containsKey("case_number")) {
                pacerCases.put((String)map.get("case_number"),map);
            }
        });

        Map<String,Map<String,Object>> matchingCases = new HashMap<>();
        cases.forEach((caseNum,map)->{
            if(pacerCases.containsKey(caseNum)) {
                Map<String,Object> newMap = new HashMap<>(map);
                newMap.putAll(pacerCases.get(caseNum));
                matchingCases.put(caseNum,newMap);
            }
        });

        System.out.println("Total normal cases: "+cases.size());
        System.out.println("Total pacer cases: "+pacerCases.size());
        System.out.println("Total matching cases: "+matchingCases.size());

        matchingCases.forEach((caseNum,map)->{
            String involved = (String)map.get("case_name");
            if(involved!=null) {
                String[] parties = involved.toUpperCase().split("V\\.");
                if(parties.length==2) {
                    String plaintiff = parties[0].trim();
                    String defendant = parties[1].trim();
                    System.out.println("Plaintiff: "+plaintiff);
                    System.out.println("Defendant: "+defendant);
                    System.out.println("Date: "+map.get("date_filed"));
                }
            }
        });


        System.out.println("Total normal cases: "+cases.size());
        System.out.println("Total pacer cases: "+pacerCases.size());
        System.out.println("Total matching cases: "+matchingCases.size());

        gzip.close();
    }
}
