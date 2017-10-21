package seeding.ai_db_updater.tools;

/**
 * Created by Evan on 9/26/2017.
 */
public class Helper {
    public static String fixPunctuationSpaces(String text) {
        String noPunct = text.replaceAll("\\s+"," ").replace(",",", ").replace(";", "; ").replace(".", ". ").replace(":", ": ").trim();
        while(noPunct.contains("  ")) noPunct = noPunct.replace("  "," ");
        return noPunct;
    }
}
