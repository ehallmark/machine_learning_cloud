package ui_models.engines;

import j2html.tags.Tag;
import lombok.Getter;
import seeding.Constants;
import seeding.Database;
import server.SimilarPatentServer;
import spark.Request;
import ui_models.portfolios.PortfolioList;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static server.SimilarPatentServer.*;

/**
 * Created by ehallmark on 2/28/17.
 */
public class SimilarityEngine extends AbstractSimilarityEngine {
    @Getter
    private List<AbstractSimilarityEngine> engines;
    public SimilarityEngine(List<AbstractSimilarityEngine> engines) {
        super(Constants.SIMILARITY);
        this.engines=engines;
    }


    @Override
    public void extractRelevantInformationFromParams(Request req) {
        int limit = extractInt(req, LIMIT_FIELD, 10);
        String comparator = extractString(req, COMPARATOR_FIELD, Constants.SIMILARITY);
        List<String> similarityEngines = extractArray(req, SIMILARITY_ENGINES_ARRAY_FIELD);
        AtomicReference<PortfolioList> ref = new AtomicReference<>(new PortfolioList(new ArrayList<>()));
        engines.forEach(engine->{
            if(similarityEngines.contains(engine.getName())) {
                engine.extractRelevantInformationFromParams(req);
                try {
                    ref.set(engine.getPortfolioList().merge(ref.get(), comparator, limit));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Sorting: " + e.getMessage());
                }
            }
        });
        portfolioList = ref.get();
        portfolioList.init(comparator,limit);
    }

    @Override
    protected Collection<String> getInputsToSearchFor(Request req) {
        throw new UnsupportedOperationException();
    }

    public Map<String,AbstractSimilarityEngine> getEngineMap() {
        return engines.stream().collect(Collectors.toMap(e->e.getName(),e->e));
    }
}
