package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;

import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class SimilarityAttribute implements AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        throw new UnsupportedOperationException("attributesFor is unsupported for similarity attribute.");
    }

    @Override
    public String getName() {
        return Constants.SIMILARITY;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }
}
