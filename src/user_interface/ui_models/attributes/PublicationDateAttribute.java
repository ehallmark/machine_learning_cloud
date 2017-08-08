package user_interface.ui_models.attributes;

import j2html.tags.Tag;
import seeding.Constants;
import seeding.Database;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

import static j2html.TagCreator.div;

/**
 * Created by ehallmark on 7/20/17.
 */
public class PublicationDateAttribute extends AbstractAttribute<String> {

    @Override
    public String attributesFor(Collection<String> portfolio, int limit) {
        String item = portfolio.stream().findAny().get();
        LocalDate date = Database.getPublicationDateFor(item, Database.isApplication(item));
        if(date==null) return "";
        return date.format(DateTimeFormatter.ISO_DATE);
    }

    @Override
    public String getName() {
        return Constants.PUBLICATION_DATE;
    }

    @Override
    public Tag getOptionsTag() {
        return div();
    }

    @Override
    public String getType() {
        return "date";
    }
}
