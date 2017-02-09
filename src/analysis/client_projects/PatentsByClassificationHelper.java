package analysis.client_projects;

import seeding.Database;
import tools.ClassCodeHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by ehallmark on 2/9/17.
 */
public class PatentsByClassificationHelper {
    public static Collection<String> getPatentsFromCPCS(Collection<String> cpcs) {
        Set<String> patents = new HashSet<>();
        cpcs.forEach(cpc->patents.addAll(Database.selectPatentNumbersFromExactClassCode(ClassCodeHandler.convertToLabelFormat(cpc))));
        return patents;
    }

    public static void main(String[] args) throws IOException {
        
    }
}
