package ui_models.portfolios.attributes;

import seeding.Constants;
import seeding.Database;
import ui_models.attributes.AbstractAttribute;
import ui_models.attributes.classification.ClassificationAttr;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Created by Evan on 6/17/2017.
 */
public class TechnologyAttribute implements AbstractAttribute<String> {
    private ClassificationAttr tagger;
    public TechnologyAttribute(ClassificationAttr tagger) {
        this.tagger=tagger;
    }
    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        return String.join("; ",tagger.attributesFor(portfolio,limit).stream().map(p->p.getFirst()).collect(Collectors.toList()));
    }

    @Override
    public String getName() {
        return Constants.TECHNOLOGY;
    }
}
