package test;

import seeding.Database;

import java.util.Collection;

/**
 * Created by Evan on 7/15/2017.
 */
public class TestApplications {
    public static void main(String[] args) {
        //Collection<String> apps = Database.getCopyOfAllApplications();
        Database.getAppToOriginalAssigneeMap().forEach((app,assignees)->{
            System.out.println("Is application? "+Database.isApplication(app));
            System.out.println(app+": "+String.join("; ",assignees));
            Collection<String> shouldBe = Database.assigneesFor(app);
            System.out.println(app+" should have: "+String.join("; ",shouldBe));
            if(shouldBe.isEmpty()) {
                System.out.println("should be is empty");
                System.exit(1);
            }
        });
    }
}
