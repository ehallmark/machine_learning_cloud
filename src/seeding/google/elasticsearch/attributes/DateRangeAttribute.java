package seeding.google.elasticsearch.attributes;

import java.time.LocalDate;

public interface DateRangeAttribute {
    default LocalDate getMinDate() { return LocalDate.now().minusYears(20).withMonth(1).withDayOfMonth(1); }
    default LocalDate getMaxDate() { return LocalDate.now().plusYears(1).withMonth(1).withDayOfMonth(1); }
}
