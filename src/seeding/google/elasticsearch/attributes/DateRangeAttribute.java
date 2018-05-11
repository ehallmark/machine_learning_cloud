package seeding.google.elasticsearch.attributes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

public interface DateRangeAttribute {
    default LocalDate getMinDate() { return LocalDate.now().minusYears(20).withMonth(1).withDayOfMonth(1); }
    default LocalDate getMaxDate() { return LocalDate.now().plusYears(1).withMonth(1).withDayOfMonth(1); }
    default String dateFormatString() { return "yyyy"; }
    default TemporalAmount timeInterval() { return Duration.of(1L, ChronoUnit.YEARS); }

}
