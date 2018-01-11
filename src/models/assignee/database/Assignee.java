package models.assignee.database;

import java.time.LocalDate;

/**
 * Created by ehallmark on 1/10/18.
 */
public class Assignee {
    String name;
    String normalizedName;
    String city;
    String state;
    String country;
    String role;
    boolean human;
    public Assignee(String name, String normalizedName, String city, String state, String country, String role, boolean human) {
        this.name=name;
        this.normalizedName=normalizedName;
        this.city=city;
        this.state=state;
        this.country=country;
        this.role=role;
        this.human=human;
    }

}