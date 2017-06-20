package ui_models.attributes.value;

import j2html.tags.Tag;
import seeding.Constants;
import server.SimilarPatentServer;
import ui_models.portfolios.attributes.DoNothing;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static j2html.TagCreator.*;
import static j2html.TagCreator.br;
import static j2html.TagCreator.label;
import static server.SimilarPatentServer.SIMILARITY_MODEL_FIELD;

/**
 * Created by Evan on 6/20/2017.
 */
public class DummySimilarityEvaluator extends ValueAttr implements DoNothing<Double> {
    public DummySimilarityEvaluator() {
        super(ValueMapNormalizer.DistributionType.None, "");
    }

    @Override
    protected List<Map<String, Double>> loadModels() {
        return Collections.emptyList();
    }

    @Override
    public Tag getOptionsTag() {
        return div().with(
                h5("Similarity Model"),select().withName(SIMILARITY_MODEL_FIELD).with(
                        option().withValue(Constants.PARAGRAPH_VECTOR_MODEL).attr("selected","true").withText("Claim Language Model"),
                        option().withValue(Constants.SIM_RANK_MODEL).withText("Citation Graph Model (patents only)"),
                        option().withValue(Constants.WIPO_MODEL).withText("WIPO Technology Model"),
                        option().withValue(Constants.CPC_MODEL).withText("CPC Code Model")
                ),br(),
                h5("Search For"),
                label("Patents (1 per line)"),br(),
                textarea().withName(SimilarPatentServer.PATENTS_TO_SEARCH_FOR_FIELD), br(),
                label("Assignees (1 per line)"),br(),
                textarea().withName(SimilarPatentServer.ASSIGNEES_TO_SEARCH_FOR_FIELD), br(),
                label("Gather Technology"),br(),
                SimilarPatentServer.gatherTechnologySelect(SimilarPatentServer.TECHNOLOGIES_TO_SEARCH_FOR_ARRAY_FIELD)
        );
    }
}
