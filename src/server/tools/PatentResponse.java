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

    public PatentResponse(List<PatentList> patents, boolean findDissimilar, List<Pair<String,Float>> keyWordList, double timeToComplete) {
        super("PATENT_RESPONSE", to_html_table(patents, findDissimilar, keyWordList, timeToComplete).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, boolean findDissimilar, List<Pair<String,Float>> keyWordList, double time) {
        // List
        Tag keywords = null;
        if (keyWordList != null) {
            List<Tag> headers = Arrays.asList(tr().with(th().attr("colspan", "2").attr("style", "text-align: left;").with(h3("Predicted Key Phrases"))), tr().with(th("Word").attr("style", "text-align: left;"), th("Score").attr("style", "text-align: left;")));
            keywords = table().with(headers).with(
                    keyWordList.stream().map(k -> tr().with(td(k.getFirst()), td(k.getSecond().toString()))).collect(Collectors.toList())
            );
        }
        String similarName = findDissimilar ? "Dissimilar" : "Similar";
        List<Tag> patents = null;
        if (patentLists != null) {
            patentLists.stream().sorted().map(patentList ->
                    div().with(
                            table().with(
                                    thead().with(
                                            tr().with(th().attr("colspan", "3").attr("style", "text-align: left;").with(
                                                    h3().with(label(similarName + " " + patentList.getName1() + " to " + patentList.getName2()))
                                            )),
                                            tr().with(th().attr("colspan", "3").attr("style", "text-align: left;").with(
                                                    label("Distributed Average Similarity: " + patentList.getAvgSimilarity())
                                            )),
                                            tr().with(
                                                    th("Patent #").attr("style", "text-align: left;"),
                                                    th("Cosine Similarity").attr("style", "text-align: left;"),
                                                    th("Invention Title").attr("style", "text-align: left;")
                                            )
                                    ),
                                    tbody().with(
                                            patentList.getPatents().stream().sorted((o1, o2) -> findDissimilar ? Double.compare(o1.getSimilarity(), o2.getSimilarity()) : Double.compare(o2.getSimilarity(), o1.getSimilarity())
                                            ).map(patent ->
                                                    tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US" + patent.getName().split("\\s+")[0])), td(Double.toString(patent.getSimilarity())), td(patent.getTitle()))
                                            ).collect(Collectors.toList())
                                    )
                            ), br())
            ).collect(Collectors.toList());
            if (findDissimilar) Collections.reverse(patents);
        }
        return div().with(
                div().with(label(Double.toString(time)+" seconds to complete.")),br(),
                keywords==null?br():keywords,br(),
                patents==null?br():div().with(patents)
        );

    }
}
