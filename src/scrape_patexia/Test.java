package scrape_patexia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Test {
    public static void main(String[] args) {
        LocalDate date = LocalDate.parse("Aug 7, 2007", DateTimeFormatter.ofPattern("MMM d, yyyy"));
        System.out.println("Date: "+date);
    }
}
