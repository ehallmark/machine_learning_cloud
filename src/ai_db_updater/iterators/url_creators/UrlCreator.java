package ai_db_updater.iterators.url_creators;

import java.time.LocalDate;

/**
 * Created by Evan on 7/5/2017.
 */
public interface UrlCreator {
    String create(LocalDate date);
}
