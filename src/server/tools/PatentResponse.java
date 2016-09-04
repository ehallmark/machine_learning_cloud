package server.tools;

import j2html.tags.Tag;
import org.deeplearning4j.berkeley.Pair;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.*;
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
        List<Tag> headers = Arrays.asList(tr().with(th("Predicted Keywords").attr("colspan","2")), tr().with(th("Word"),th("Score")));
        Tag keywords = null;
        if(keyWordList!=null) {
            headers.addAll(keyWordList.stream().map(k->tr().with(td(k.getFirst()),td(k.getSecond().toString()))).collect(Collectors.toList()));
            keywords = table().with(
                headers
            );
        }
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
                keywords==null?div():keywords,br(),
                div().with(patents)
        );

    }
}
