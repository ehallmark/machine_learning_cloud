package test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Created by Evan on 8/8/2017.
 */
public class Random {
    public static void main(String[] args) {
        System.out.println(LocalDate.parse("19720400", DateTimeFormatter.BASIC_ISO_DATE));
        System.out.println(Integer.parseInt("0005"));
    }
}
