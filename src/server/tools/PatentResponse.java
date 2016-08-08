package server.tools;

import j2html.tags.Tag;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {
    public PatentResponse(List<PatentList> patents, String query) {
        super(query, to_html_table(patents,query).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, String query) {
        // List
        return div().with(
                h3().with(label("Similar Patents to: "),a(query).withHref("https://www.google.com/patents/US"+query)),br(),br(),
                div().with(patentLists.stream().map(patentList ->
                    div().with(table().with(
                            thead().with(
                                    tr().with(th("By Similarity of "+patentList.getBySimilarityTo()).attr("colspan","2")),
                                    tr().with(
                                            th("Patent #"),
                                            th("Cosine Similarity")
                                    )
                            ),
                            tbody().with(
                                    patentList.getPatents().stream().map(patent->
                                            tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US"+patent.getName().split("\\s+")[0])),td(Double.toString(patent.getSimilarity())))
                                    ).collect(Collectors.toList())
                            )
                    ),br())
                ).collect(Collectors.toList()))
        );

    }
}
