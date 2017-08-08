package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;
import tools.ClassCodeHandler;

import java.util.Collection;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 6/15/17.
 */
public class CPCAttribute extends AbstractAttribute<String[]> {

    @Override
    public String[] attributesFor(Collection<String> portfolio, int limit) {
        if(portfolio.isEmpty()) return new String[]{};
        String item = portfolio.stream().findAny().get();
        if(Database.isAssignee(item)) return new String[]{};
        else {
            Collection<String> cpcs = Database.classificationsFor(item).stream().map(cpc-> ClassCodeHandler.convertToHumanFormat(cpc)).collect(Collectors.toList());
            return cpcs.toArray(new String[cpcs.size()]);
        }
    }

    @Override
    public String getName() {
        return Constants.CPC_CODES;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "keyword";
    }
}
