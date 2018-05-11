package seeding.google.elasticsearch.attributes;

import java.time.LocalDate;

public interface DateRangeAttribute {
    default LocalDate getMinDate() { return LocalDate.now().minusYears(20); }
    default LocalDate getMaxDate() { return LocalDate.now(); }
}
