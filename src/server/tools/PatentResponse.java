package server.tools;

import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {

    public PatentResponse(List<PatentList> patents, boolean findDissimilar, List<Pair<String,Float>> keyWordList) {
        super("PATENT_RESPONSE", to_html_table(patents, findDissimilar, keyWordList).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, boolean findDissimilar, List<Pair<String,Float>> keyWordList) {
        // List
        List<Tag> keywords = keyWordList==null?null:keyWordList.stream().map(k->div().with(label(k.getFirst()+": "+k.getSecond()),br())).collect(Collectors.toList());
        String similarName = findDissimilar ? "Dissimilar" : "Similar";
        List<Tag> patents = patentLists.stream().sorted().map(patentList ->
                div().with(
                        table().with(
                                thead().with(
                                        tr().with(th().attr("colspan","3").with(
                                                h3().with(label(similarName+" "+patentList.getName1()+" to "+patentList.getName2()))
                                        )),
                                        tr().with(th().attr("colspan","3").with(
                                                label("Distributed Average Similarity: "+patentList.getAvgSimilarity())
                                        )),
                                        tr().with(
                                                th("Patent #"),
                                                th("Cosine Similarity"),
                                                th("Invention Title")
                                        )
                                ),
                                tbody().with(
                                        patentList.getPatents().stream().sorted((o1,o2)->findDissimilar ? Double.compare(o1.getSimilarity(),o2.getSimilarity()) : Double.compare(o2.getSimilarity(),o1.getSimilarity())
                                        ).map(patent->
                                                tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US"+patent.getName().split("\\s+")[0])),td(Double.toString(patent.getSimilarity())),td(patent.getTitle()))
                                        ).collect(Collectors.toList())
                                )
                        ),br())
        ).collect(Collectors.toList());
        if(findDissimilar) Collections.reverse(patents);
        return div().with(
                div().with(keywords==null?new ArrayList<Tag>():keywords),
                div().with(patents)
        );

    }
}
