package seeding;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 8/11/16.
 */
public class SeedCompanyVectors {
    private static final String USER_AGENT = "Mozilla/5.0";

    // HTTP POST request
    private static void createCandidateSetPost(String candidateSetName, String assignee, String patents) throws Exception {
        assert candidateSetName!=null && !(assignee==null && patents==null);
        String url = "http://192.168.1.148:4568/create";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = null;
        if(patents==null || patents.length() == 0) urlParameters = "name="+candidateSetName+"&assignee="+assignee;
        else if(assignee == null || assignee.trim().length() == 0) {
            urlParameters = "name="+candidateSetName+"&patents="+patents;
            //if(innerNames!=null&&innerNames.size()>0) urlParameters+="&names="+String.join(">><<",innerNames);
        }
        else throw new RuntimeException("Invalid parameters!");

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        //System.out.println(response.toString());

    }

    private static void createCandidateGroupPost(String groupPrefix) throws Exception {
        assert groupPrefix!=null;
        String url = "http://192.168.1.148:4568/create_group";
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = "group_prefix="+groupPrefix;

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        //System.out.println(response.toString());

    }

    public static void main(String[] args) throws Exception {
        /*createCandidateSetPost("Huawei", "huawei", null);
        createCandidateSetPost("Panasonic", "panasonic", null);
        createCandidateSetPost("Sony", "sony", null);
        createCandidateSetPost("ZTE", "zte", null);
        createCandidateSetPost("Orange", "orange", null);
        createCandidateSetPost("Cisco", "cisco", null);
        createCandidateSetPost("Telia Custom", null, String.join(" ",Arrays.asList(Constants.CUSTOM_TELIA_PATENT_LIST.split("\\s+"))));

        createCandidateSetPost("Verizon", "verizon", null);
        // ETSI PATENTS!
        createCandidateSetPost("ETSI (all)", null, String.join(" ",Constants.ETSI_PATENT_LIST));

        {
            String ETSI_PREFIX = "ETSI -";
            createCandidateGroupPost(ETSI_PREFIX);
            Map<String, List<String>> ETSIMap = GetEtsiPatentsList.getETSIPatentMap();
            for (Map.Entry<String, List<String>> e : ETSIMap.entrySet()) {
                createCandidateSetPost(ETSI_PREFIX+" "+e.getKey(), null, String.join(" ", e.getValue()));
            }
        }
        */
        {
            String ratingsPrefix = "Gather Ratings -";
            createCandidateGroupPost(ratingsPrefix);
            Map<String, List<String>> ratingsMap = Database.getGatherRatingsMap();
            for (Map.Entry<String, List<String>> e : ratingsMap.entrySet()) {
                createCandidateSetPost(ratingsPrefix+" "+e.getKey(), null, String.join(" ", e.getValue()));
            }
        }

        //createCandidateSetPost("AT%26T Custom", null, String.join(" ",Arrays.asList(Constants.CUSTOM_ATT_LIST)));
        //createCandidateSetPost("SIE Custom", null, String.join(" ",GetEtsiPatentsList.getExcelList(new File("sie.xls"),3,1)));
        //System.out.println(String.join(" ",GetEtsiPatentsList.getExcelList(new File("sie.xls"),3,1)));
        //createCandidateSetPost("AutoConnect", null, String.join(" ",GetEtsiPatentsList.getExcelList(new File("autoconnect.xls"),5,1)));
        //createCandidateSetPost("AT%26T", "at %26 t", null);

        /*
        Database.setupSeedConn();
        System.out.println("Trying to seed adobe venture...");
        // gather patents
        {
            File csv = new File("adobe_venture_patents_pitchbook.csv");
            File csv2 = new File("adobe_venture_patents_pitchbook2.csv");
            List<String> companies = new ArrayList<>(new HashSet<>(GetEtsiPatentsList.getExcelList(new File("adobe_venture_patents_filtered.xls"),0,1)));
            //List<String> patents = GetEtsiPatentsList.getExcelList(new File("adobe_venture_patents_filtered.xls"),1,1);
            //assert companies.size()==patents.size() : "Lists are not the same length!";
            String ADOBE_PREFIX = "Adobe Venture - ";
            BufferedWriter bw = new BufferedWriter(new FileWriter(csv));
            BufferedWriter bw2 = new BufferedWriter(new FileWriter(csv2));
            bw.write("Pitchbook Company,Patent,USPTO AbstractAssignee\n");
            bw2.write("Pitchbook Company,Patents\n");
            bw.flush();
            bw2.flush();
            //createCandidateGroupPost(ADOBE_PREFIX);
            for(int i = 0; i < companies.size(); i++) {
                //createCandidateSetPost(ADOBE_PREFIX + companies.get(i), null, patents.get(i));
                List<String> patents = Database.selectAllDocumentsByAssignee(companies.get(i));
                if(patents.size()>0) {
                    bw2.write(companies.get(i)+","+String.join(" ", patents)+"\n");
                    bw2.flush();
                    for(String patent : patents) {
                        bw.write(companies.get(i)+","+patent+","+Database.getAssigneeFromDB(patent)+"\n");
                    }
                    bw.flush();
                }
            }
            bw.flush();
            bw.close();
            bw2.flush();
            bw2.close();
        }
        Database.close();
        */

        //Database.setupSeedConn();
        //Database.setupGatherConn();
        /*
        List<String> list = GetEtsiPatentsList.getExcelList(new File("rakuten_key_assets.xls"),0,1);
        System.out.println("Trying to get patents...");
        // gather patents
        {
            String PREFIX = "Rakuten Key Assets - ";
            createCandidateGroupPost(PREFIX);
            for (String patent : list) {
                createCandidateSetPost(PREFIX + patent, null, patent);
                System.out.println(patent);
            }
        }
        //Database.close();
            */
    }
}
