package stocks;

import assignee_normalization.AssigneeTrimmer;
import assignee_normalization.NormalizeAssignees;
import com.googlecode.concurrenttrees.radix.RadixTree;
import info.debatty.java.stringsimilarity.JaroWinkler;
import info.debatty.java.stringsimilarity.interfaces.StringDistance;
import org.nd4j.linalg.primitives.Pair;
import seeding.Constants;
import seeding.Database;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 11/16/17.
 */
public class ScrapeCompanyTickers {
    private static final File csvFile = new File(Constants.DATA_FOLDER+"yahoo_companies_and_symbols.csv");

    public static void main(String[] args) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(csvFile));

        //Set<String> allAssignees = new HashSet<>(Database.getAssignees());

        RadixTree<String> trie = Database.getNormalizedAssigneePrefixTrie();
        JaroWinkler distance = new JaroWinkler();

        NormalizeAssignees normalizer = new NormalizeAssignees();

        AtomicInteger idx = new AtomicInteger(0);
        Map<String,Set<String>> companyToTickersMap = reader.lines().parallel().map(line->{
            if(idx.getAndIncrement()%10000==9999) {
                System.out.println("Read: "+idx.get());
            }
            String[] cells = line.split(",");
            if(cells.length<2) return null;
            String symbol = cells[0];
            String company = cells[1];
            if(symbol!=null) symbol = symbol.trim();
            if(company!=null) company = AssigneeTrimmer.standardizedAssignee(company);
            if(symbol.isEmpty()||company.isEmpty()||!trie.getValuesForClosestKeys(company).iterator().hasNext()) {
                return null;
            }
            final String normalizedCompany = normalizer.normalizedAssignee(company);
            if(normalizedCompany == null || company.isEmpty()) return null;
            List<String> possible = new ArrayList<>();
            trie.getValuesForClosestKeys(company).forEach(a->possible.add(a));
            Pair<String,Double> companyScorePair = possible.stream()
                    .map(p->new Pair<>(p,distance.similarity(p,normalizedCompany)))
                    .sorted((p1,p2)->p2.getSecond().compareTo(p1.getSecond())).findFirst().get();

            double score = companyScorePair.getSecond();
            String assignee = companyScorePair.getFirst();
            if(score>=0.9 && Math.min(normalizedCompany.length(),assignee.length()) > 3) {
                System.out.println("Changing " + normalizedCompany + " to " + assignee + " with score: " + score);
                return new Pair<>(assignee, symbol);
            } else return null;
        }).filter(p->p!=null).collect(Collectors.groupingBy(p->p.getFirst(),Collectors.mapping(p->p.getSecond(),Collectors.toSet())));

        System.out.println("Companies found: "+companyToTickersMap.size());
    }
}
