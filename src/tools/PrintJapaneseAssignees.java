package tools;

import seeding.Database;

import java.util.Collection;
import java.util.Collections;

/**
 * Created by ehallmark on 5/8/17.
 */
public class PrintJapaneseAssignees {
    public static void main(String[] args) {
        Collection<String> japaneseAssignees = Database.getJapaneseCompanies();
        System.out.println(String.join("\n",japaneseAssignees));
    }
}
