package analysis.patent_view_api;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import seeding.Database;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 2/5/2017.
 */
public class PatentAPIHandler {
    public static List<Patent> requestAllPatentsFromClassCodes(Collection<String> classCodes) {
        PatentQuery query = new PatentQuery(classCodes,1);
        int totalResults;
        try {
            totalResults = requestPatents(query).getTotalPatentCount();
        } catch(Exception e) {
            e.printStackTrace();
            totalResults=0;
        }
        if(totalResults==0) return new ArrayList<>();
        List<Patent> results = new ArrayList<>(totalResults);
        int page = 1;
        while(results.size()<totalResults) {
            query = new PatentQuery(classCodes,page);
            try {
                results.addAll(requestPatents(query).getPatents());
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
            page++;
        }
        return results;
    }

    public static List<Patent> requestAllPatents(Collection<String> patents) {
        AllPatentQuery query = new AllPatentQuery(patents,1);
        int totalResults;
        List<Patent> results=null;
        try {
            PatentResponse response = requestPatents(query);
            totalResults = response.getTotalPatentCount();
            if(totalResults==0) return new ArrayList<>();
            results = new ArrayList<>(totalResults);
            results.addAll(response.getPatents());
        } catch(Exception e) {
            e.printStackTrace();
            totalResults=0;
        }
        if(results==null) return new ArrayList<>();
        int page = 2;
        while(results.size()<totalResults) {
            query = new AllPatentQuery(patents,page);
            try {
                results.addAll(requestPatents(query).getPatents());
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
            page++;
        }
        return results;
    }

    public static List<Patent> requestAllPatentsFromKeywords(Collection<String> keywords) {
        PatentKeywordQuery query = new PatentKeywordQuery(keywords,1);
        int totalResults;
        try {
            totalResults = requestPatents(query).getTotalPatentCount();
        } catch(Exception e) {
            e.printStackTrace();
            totalResults=0;
        }
        if(totalResults==0) return new ArrayList<>();
        List<Patent> results = new ArrayList<>(totalResults);
        int page = 1;
        while(results.size()<totalResults) {
            query = new PatentKeywordQuery(keywords,page);
            try {
                results.addAll(requestPatents(query).getPatents());
            } catch(Exception e) {
                e.printStackTrace();
                break;
            }
            page++;
        }
        return results;
    }

    private static PatentResponse requestPatents(Query query) {
        try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
            URI uri = new URIBuilder()
                    .setScheme("http")
                    .setHost("www.patentsview.org")
                    .setPath("/api/patents/query")
                    .setCustomQuery(query.toString())
                    .build();
            System.out.println("URI: "+uri.toString());
            HttpGet request = new HttpGet(uri.toString());
            request.addHeader("Accept","application/json");
            request.addHeader("Content-Type","application/json");

            HttpResponse result = httpClient.execute(request);
            String json = EntityUtils.toString(result.getEntity(), "UTF-8");
            com.google.gson.Gson gson = new com.google.gson.Gson();
            PatentResponse response = gson.fromJson(json, PatentResponse.class);

            System.out.println("Total patent count: "+response.getTotalPatentCount()+" patents");
            System.out.println("Total patent count: "+response.getTotalPatentCount()+" patents");

            return response;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static void addResultsToAssigneeMap(List<Patent> patents, Map<String,Set<String>> map) {
        patents.forEach(patent->{
            String patNum = patent.getPatentNumber();
            patent.getAssignees().forEach(assignee->{
                if(assignee.getAssignee()==null) return;
                String assigneeName = assignee.getAssignee();
                assigneeName = tools.AssigneeTrimmer.standardizedAssignee(assigneeName);
                if(map.containsKey(assigneeName)) {
                    map.get(assigneeName).add(patNum);
                } else {
                    Set<String> set = new HashSet<>();
                    set.add(patNum);
                    map.put(assigneeName,set);
                }
            });
        });
    }

    public static void writeAssigneeDataCountsToCSV(Map<String,Set<String>> assigneeToPatentsMap, File file) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            for (Map.Entry<String, Set<String>> e : assigneeToPatentsMap.entrySet().stream().sorted((e1,e2)->e1.getKey().compareTo(e2.getKey())).collect(Collectors.toList())) {
                String assignee = e.getKey();
                Set<String> patents = e.getValue();
                String line = assignee + "," + patents.size() + "\n";
                writer.write(line);
                writer.flush();
            }

            writer.flush();
            writer.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static void groupImportantAssignees(Map<String,Set<String>> map, Collection<String> importantAssignees) {
        // consolidate assignees
        Map<String,Set<String>> newMap = new HashMap<>();
        importantAssignees.forEach(assignee->newMap.put(assignee,new HashSet<>()));
        for(Map.Entry<String,Set<String>> e : map.entrySet()) {
            String importantAssignee = null;
            for(String assignee: importantAssignees) {
                if(e.getKey().contains(assignee)) {
                    importantAssignee=assignee;
                    break;
                }
            }
            if(importantAssignee==null) {
                if(newMap.containsKey(e.getKey())) {
                    newMap.get(e.getKey()).addAll(e.getValue());
                } else {
                    Set<String> set = new HashSet<>();
                    set.addAll(e.getValue());
                    newMap.put(e.getKey(),set);
                }
            } else {
                newMap.get(importantAssignee).addAll(e.getValue());
            }
        }
        map.clear();
        map.putAll(newMap);
    }

    private static List<String> loadKeywordFile(File file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(file));
        List<String> keywords = reader.lines()
                .filter(line->line!=null&&line.trim().length()>0)
                .map(line->line.trim().replaceAll("_"," "))
                .limit(30)
                .collect(Collectors.toList());
        System.out.println("Keywords: "+String.join("; ",keywords));
        reader.close();
        return keywords;
    }

    public static void main(String[] args) throws IOException{
        /*
            List<Patent> patents = requestAllPatentsFromAssigneesAndClassCodes(Arrays.asList("microsoft","panasonic"),Arrays.asList("G06F3\\/0383"));
        */
        List<String> importantAssignees = Arrays.asList("Nokia", "Samsung", "Ericsson", "Panasonic", "NTT DoCoMo", "IP Bridge", "Sisvel", "Philips","Microsoft")
                .stream()
                .map(a->a.toUpperCase())
                .collect(Collectors.toList());
        {
            Map<String, Set<String>> assigneeTo2GMap = new HashMap<>();
            List<Patent> patents2G = requestAllPatentsFromKeywords(loadKeywordFile(new File("ms_sep_2g_keywords.csv")));
            System.out.println("Total 2G patents found: " + patents2G.size());
            addResultsToAssigneeMap(patents2G, assigneeTo2GMap);
            groupImportantAssignees(assigneeTo2GMap,importantAssignees);
            Database.trySaveObject(assigneeTo2GMap,new File("assignee_to_2g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo2GMap,new File("2g_assignee_data.csv"));
        }
        {
            Map<String,Set<String>> assigneeTo3GMap = new HashMap<>();
            List<Patent> patents3G = requestAllPatentsFromKeywords(loadKeywordFile(new File("ms_sep_3g_keywords.csv")));
            System.out.println("Total 4G patents found: "+patents3G.size());
            addResultsToAssigneeMap(patents3G, assigneeTo3GMap);
            groupImportantAssignees(assigneeTo3GMap,importantAssignees);
            Database.trySaveObject(assigneeTo3GMap,new File("assignee_to_3g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo3GMap,new File("3g_assignee_data.csv"));
        }
        {
            List<Patent> patents4G = requestAllPatentsFromKeywords(loadKeywordFile(new File("ms_sep_4g_keywords.csv")));
            System.out.println("Total 4G patents found: " + patents4G.size());
            Map<String, Set<String>> assigneeTo4GMap = new HashMap<>();
            addResultsToAssigneeMap(patents4G, assigneeTo4GMap);
            groupImportantAssignees(assigneeTo4GMap,importantAssignees);
            Database.trySaveObject(assigneeTo4GMap,new File("assignee_to_4g_patents_map.jobj"));
            writeAssigneeDataCountsToCSV(assigneeTo4GMap,new File("4g_assignee_data.csv"));
        }

    }
}
