package server.tools;

import j2html.tags.Tag;
import tools.PatentList;

import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;

/**
 * Created by ehallmark on 7/27/16.
 */
public class CandidateComparisonResponse extends ServerResponse {
    public CandidateComparisonResponse(List<PatentList> patents, String candidate1, String candidate2) {
        super("", to_html_table(patents,candidate1,candidate2).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, String candidate1, String candidate2) {
        // List
        return div().with(
                h3("Similar Patents between "+candidate1+" and "+candidate2),br(),
                div().with(patentLists.stream().map(patentList ->
                    div().with(table().with(
                            thead().with(
                                    tr().with(
                                            th("Patent # from "+candidate1),
                                            th("Patent # from "+candidate2),
                                            th("Cosine Similarity")
                                    )
                            ),
                            tbody().with(
                                    patentList.getPatents().stream().map(patent->
                                            tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US"+patent.getName().split("\\s+")[0])),
                                                    td().with(a(patent.getReferringName()).withHref("https://www.google.com/patents/US"+patent.getReferringName().split("\\s+")[0])),
                                                    td(Double.toString(patent.getSimilarity())))
                                    ).collect(Collectors.toList())
                            )
                    ),br())
                ).collect(Collectors.toList()))
        );

    }
}
