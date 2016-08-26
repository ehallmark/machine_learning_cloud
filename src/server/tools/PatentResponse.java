package server.tools;

import j2html.tags.Tag;
import tools.PatentList;
import static j2html.TagCreator.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/27/16.
 */
public class PatentResponse extends ServerResponse {
    public PatentResponse(List<PatentList> patents, String query, boolean findDissimilar) {
        this(patents,query,findDissimilar,true);
    }

    public PatentResponse(List<PatentList> patents, String query, boolean findDissimilar, boolean includeLink) {
        super(query, to_html_table(patents,query,findDissimilar, includeLink).render(),patents);
    }

    private static Tag to_html_table(List<PatentList> patentLists, String query, boolean findDissimilar, boolean includeLinks) {
        // List
        String similarName = findDissimilar ? "Dissimilar" : "Similar";
        Tag titleTag;
        if(includeLinks) titleTag = h3().with(label(similarName+" patents to: "),a(query).withHref("https://www.google.com/patents/US"+query));
        else {
            String name1 = query.split("|")[0];
            String name2 = query.split("|")[1];
            titleTag = h3().with(label(similarName+" "+name1+" patents to "+name2+" candidate set"));
        }
        return div().with(
                titleTag,br(),
                div().with(patentLists.stream().map(patentList ->
                    div().with(table().with(
                            thead().with(
                                    tr().with(
                                            th("Patent #"),
                                            th("Cosine Similarity")
                                    )
                            ),
                            tbody().with(
                                    patentList.getPatents().stream().sorted(new Comparator<AbstractPatent>() {
                                        @Override
                                        public int compare(AbstractPatent o1, AbstractPatent o2) {
                                            return findDissimilar ? Double.compare(o1.getSimilarity(),o2.getSimilarity()) : Double.compare(o2.getSimilarity(),o1.getSimilarity());
                                        }
                                    }).map(patent->
                                            tr().with(td().with(a(patent.getName()).withHref("https://www.google.com/patents/US"+patent.getName().split("\\s+")[0])),td(Double.toString(patent.getSimilarity())))
                                    ).collect(Collectors.toList())
                            )
                    ),br())
                ).collect(Collectors.toList()))
        );

    }
}
