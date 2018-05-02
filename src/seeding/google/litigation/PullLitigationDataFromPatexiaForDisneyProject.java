package seeding.google.litigation;

import seeding.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class PullLitigationDataFromPatexiaForDisneyProject {

    private static String anyMatch(String c, Collection<String> collection) {
        return collection.stream().filter(p->c.toLowerCase().startsWith(p.toLowerCase())).findFirst().orElse(null);
    }

    public static void main(String[] args) throws Exception {
        List<String> companies = Arrays.asList(
                "apple ",
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


        Map<String,List<Map<String,Object>>> casesAsDefendant = new HashMap<>();
        Map<String,List<Map<String,Object>>> casesAsPlaintiff = new HashMap<>();

        Connection conn = Database.getConn();
        PreparedStatement ps = conn.prepareStatement("select case_number,case_name,case_date,extract(YEAR from case_date) as year,plaintiff,defendant from patexia_litigation");

        ResultSet rs = ps.executeQuery();
        while(rs.next()) {
            String caseNumber = rs.getString(1);
            String caseName = rs.getString(2);
            Date date = rs.getDate(3);
            int year = rs.getInt(4);
            String plaintiff = rs.getString(5);
            String defendant = rs.getString(6);

            Map<String,Object> map = new HashMap<>();
            map.put("plaintiff",plaintiff);
            map.put("defendant",defendant);
            map.put("case_number",caseNumber);
            map.put("case_name",caseName);
            map.put("date_filed",date);
            map.put("year",year);

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


        System.out.println("Total matched cases plaintiff: "+casesAsPlaintiff.size());
        System.out.println("Total matched cases defendant: "+casesAsDefendant.size());


        BufferedWriter writer = new BufferedWriter(new FileWriter(new File("disney_project_litigation_final.csv")));
        writer.write("Company,Is Plaintiff?,Total,Plaintiff Name,Defendant Name,Date Filed,Year,Case Number,Case Name\n");
        casesAsDefendant.forEach((company,data)->{
            for(Map<String,Object> point : data) {
                StringJoiner sj = new StringJoiner("\",\"","\"","\"\n");
                sj
                        .add(company.trim())
                        .add("FALSE")
                        .add(String.valueOf(data.size()))
                        .add(point.get("plaintiff").toString())
                        .add(point.get("defendant").toString())
                        .add(point.getOrDefault("date_filed","").toString())
                        .add(point.get("year").toString())
                        .add(point.get("case_number").toString())
                        .add(point.get("case_name").toString());
                try {
                    writer.write(sj.toString());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });

        casesAsPlaintiff.forEach((company,data)->{
            for(Map<String,Object> point : data) {
                StringJoiner sj = new StringJoiner("\",\"","\"","\"\n");
                sj
                        .add(company)
                        .add("TRUE")
                        .add(String.valueOf(data.size()))
                        .add(point.get("plaintiff").toString())
                        .add(point.get("defendant").toString())
                        .add(point.getOrDefault("date_filed","").toString())
                        .add(point.get("year").toString())
                        .add(point.get("case_number").toString())
                        .add(point.get("case_name").toString());
                try {
                    writer.write(sj.toString());
                } catch(Exception e) {
                    e.printStackTrace();
                }
            }
        });
        writer.flush();
        writer.close();

    }
}
