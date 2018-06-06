package seeding.google.elasticsearch.attributes;

import seeding.google.elasticsearch.Attributes;

import java.time.LocalDate;

/**
 * Created by ehallmark on 7/20/17.
 */
public class CalculatedExpirationDate extends DateAttribute {

    @Override
    public String getName() {
        return Attributes.EXPIRATION_DATE_ESTIMATED;
    }

    @Override
    public LocalDate getMinDate() {
        return LocalDate.now().minusYears(5);
    }

    @Override
    public LocalDate getMaxDate() {
        return LocalDate.now().plusYears(20);
    }
}
