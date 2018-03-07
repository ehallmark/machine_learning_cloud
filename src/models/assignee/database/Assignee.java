package models.assignee.database;

/**
 * Created by ehallmark on 1/10/18.
 */
public class Assignee {
    String name;
    String city;
    String state;
    String country;
    String role;
    String entityStatus;
    boolean human;
    public Assignee(String name, String city, String state, String country, String role, String entityStatus, boolean human) {
        this.name=name;
        this.city=city;
        this.state=state;
        this.country=country==null?"US":country;
        this.entityStatus=entityStatus;
        this.role=role;
        this.human=human;
    }

}