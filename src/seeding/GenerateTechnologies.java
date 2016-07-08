package seeding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenerateTechnologies {
	private GenerateTechnologies() throws Exception {
        DatabaseIterator databaseIterator = new DatabaseIterator(false);
		DatabaseIterator testIterator = new DatabaseIterator(true);
        Set<String> tech = new HashSet<>(databaseIterator.getLabels());
		tech.addAll(testIterator.getLabels());
		List<String> techList = new ArrayList<>(tech);
		techList.sort((String o1, String o2)-> o1.compareTo(o2));
        System.out.print('"'+String.join('"'+","+'"',techList)+'"');

    }

	public static void main(String[] args) {
		try {
			new GenerateTechnologies();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
}
