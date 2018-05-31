package seeding.google.postgres;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateKindCodeConcordanceTable {
    public static void main(String[] args) throws Exception {
        CsvReader reader = new CsvReader();
        reader.setContainsHeader(false);
        File file = new File("Concordance_20180328.csv");
        CsvContainer csv = reader.read(file, StandardCharsets.UTF_8);

        Map<String, List<Map<String, Object>>> allData = new HashMap<>();
        List<Map<String,Object>> countryData = null;
        String currentCC = null;
        String currentCountryName;
        List<CsvRow> rows = csv.getRows().subList(2,csv.getRows().size());
        for(CsvRow row : rows) {
            String cc = row.getField(0).trim();
            if(cc.length()>0) {
                if(currentCC!=null) {
                    allData.put(currentCC, new ArrayList<>(countryData));
                    countryData.clear();
                }
                currentCC = cc;
                currentCountryName = row.getField(8);
                System.out.println("Country "+currentCC+": "+currentCountryName);
                countryData = new ArrayList<>();
            } else {
                String kindCode = row.getField(1).trim();
                if(kindCode.length()>0) {
                    String durationApplicable = row.getField(8).trim();
                    String typeOfDocument = row.getField(9).trim();
                    System.out.println(kindCode+" -> Duration: "+durationApplicable+", Doc Type: "+typeOfDocument);
                }
            }
        }
    }
}
