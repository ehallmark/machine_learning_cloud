package server.tools;

import j2html.tags.Tag;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {

    public PatentResponse(List<PatentList> patents, boolean findDissimilar) {
        super("PATENT_RESPONSE", to_html_table(patents, findDissimilar).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, boolean findDissimilar) {
        // List
        String similarName = findDissimilar ? "Dissimilar" : "Similar";
        List<Tag> patents = patentLists.stream().sorted().map(patentList ->
                div().with(
                        h3().with(label(similarName+" "+patentList.getName1()+" to "+patentList.getName2())),
                        h5().with(label("Global Average Similarity: "+patentList.getAvgSimilarity())),
                        table().with(
                                thead().with(
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
                div().with(patents)
        );

    }
}
