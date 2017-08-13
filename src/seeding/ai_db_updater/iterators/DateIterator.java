package seeding.ai_db_updater.iterators;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Created by Evan on 8/13/2017.
 */
public interface DateIterator extends Serializable {
    void run(LocalDate startDate);
    String getZipFilePrefix();
}
