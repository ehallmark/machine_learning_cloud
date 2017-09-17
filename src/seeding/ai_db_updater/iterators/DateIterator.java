package seeding.ai_db_updater.iterators;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Created by Evan on 8/13/2017.
 */
public interface DateIterator {
    void run(LocalDate startDate, Collection<LocalDate> failedDates);
    String getZipFilePrefix();
}
