package test;

import com.googlecode.concurrenttrees.radix.RadixTree;
import info.debatty.java.stringsimilarity.JaroWinkler;
import seeding.Database;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Iterator;

public class AssigneePortfolioSizes {
    public static void main(String[] args) {
        String assigneesStr = "Tzoa\n" +
                "Everimpact\n" +
                "BlindSquare\n" +
                "BigBelly\n" +
                "Zerocycle\n" +
                "ShotSpotter\n" +
                "CityMapper\n" +
                "ParkWhiz\n" +
                "Urbiotica\n" +
                "Echelon\n" +
                "BestMile\n" +
                "Anagog\n" +
                "Streetline\n" +
                "Enevo Inc.\n" +
                "Compology\n" +
                "Ayyeka\n" +
                "Advantech\n" +
                "CityZenith\n" +
                "G Element\n" +
                "Libelium\n" +
                "Array of Things\n" +
                "Hello Lamp Post\n" +
                "AirCasting\n" +
                "UrbanAir\n" +
                "ECUBE Labs\n" +
                "IBM\n" +
                "SmartBin\n" +
                "WAVIoT\n" +
                "Citibrain\n" +
                "ecoATM\n" +
                "ParkiFi\n" +
                "IPS Group\n" +
                "twistHDM\n" +
                "World Sensing\n" +
                "Nedap Identification Systems\n" +
                "Cisco\n" +
                "Sensys Networks\n" +
                "Mindteck\n" +
                "Unifi\n" +
                "SKIDATA\n" +
                "Amano Group\n" +
                "Fensens\n" +
                "ParkMe\n" +
                "ParkNow\n" +
                "Qualcomm\n" +
                "Amazon\n" +
                "Alphabet Inc / Google\n" +
                "Wink\n" +
                "GE\n" +
                "August\n" +
                "Sylvania\n" +
                "Philips\n" +
                "Cree\n" +
                "Hampton Bay\n" +
                "Sengled\n" +
                "Halo\n" +
                "Leviton\n" +
                "Lutron\n" +
                "iHome\n" +
                "Switchmate & Wink\n" +
                "Dome\n" +
                "Ring\n" +
                "Canary\n" +
                "Kidde\n" +
                "GoControl\n" +
                "leakSMART\n" +
                "Andersen\n" +
                "Arlo\n" +
                "Pella \n" +
                "MYQ\n" +
                "Kwikset\n" +
                "Schlage\n" +
                "Yale\n" +
                "ecobee\n" +
                "Honeywell\n" +
                "Sensi\n" +
                "Carrier\n" +
                "Rachio\n" +
                "Rheem\n" +
                "Gardinier & Wink\n" +
                "Bali\n" +
                "Graber & Wink\n" +
                "Signature Series  & Wink\n" +
                "Google Inc\n" +
                "Roku\n" +
                "Apple Inc\n" +
                "FitBit\n" +
                "Amcrest\n" +
                "Bose\n" +
                "Jam Audio\n" +
                "Belkin\n" +
                "Keen\n" +
                "Logitech\n" +
                "Verizon\n" +
                "Symantec\n" +
                "BitDefender\n" +
                "Karamba Security\n" +
                "Infineon\n" +
                "Gemalto\n" +
                "Digicert\n" +
                "Trustwave\n" +
                "Omron\n" +
                "Hocoma\n" +
                "Liquid Robotics\n" +
                "Claronav\n" +
                "WhiteBoX Robotics\n" +
                "Perception Robotics\n" +
                "Automatix\n" +
                "Quanergy\n" +
                "Notion\n" +
                "Control4\n" +
                "Samsung\n" +
                "Plum\n" +
                "Lully\n" +
                "Vera\n" +
                "Loxone\n" +
                "Petcube\n" +
                "Iris by Lowes\n" +
                "Savant\n" +
                "Vivint\n" +
                "LATCH\n" +
                "iBaby\n" +
                "HomeSeer\n" +
                "SimpliSafe\n" +
                "Innit\n" +
                "Microsoft\n" +
                "PTC\n" +
                "Salesforce\n" +
                "Oracle\n" +
                "Google Inc\n" +
                "Lifx\n" +
                "Intel\n" +
                "Amazon\n" +
                "AT & T\n" +
                "Digimarc\n" +
                "EVRYTHING\n" +
                "LG Electronics\n" +
                "Broadcom\n" +
                "Blackberry LTD\n" +
                "Canon KK\n" +
                "Elgato\n" +
                "Hitachi\n" +
                "T-Mobile\n" +
                "Comcast\n" +
                "SkyWorks\n" +
                "Garmin\n" +
                "Sierra Wireless";

        String[] assignees = assigneesStr.split("\\n");

        System.out.println(assignees.length);

        RadixTree<String> assigneeTrie = Database.getAssigneePrefixTrie();

        try(BufferedWriter writer = new BufferedWriter(new FileWriter("home_automation_assignees.csv"))) {
            for (String assignee : assignees) {
                Iterator<String> closest = assigneeTrie.getValuesForClosestKeys(assignee).iterator();
                JaroWinkler jaroWinkler = new JaroWinkler();
                double best = Double.MIN_VALUE;
                String choice = null;
                while (closest.hasNext()) {
                    String option = closest.next();
                    double score = jaroWinkler.similarity(assignee, option);
                    if (score > best) {
                        choice = option;
                        best = score;
                    }
                }
                if (choice != null) {
                    System.out.println(assignee+" => "+choice);
                    writer.write("\"" + assignee + "\",\"" + choice + "\"," + Database.getAssetCountFor(choice) + "\n");
                } else {
                    writer.write("\"" + assignee + "\",(NOT FOUND),0\n");
                }
            }
            writer.flush();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
