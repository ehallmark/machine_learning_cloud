package seeding.ai_db_updater;

import seeding.ai_db_updater.iterators.AssignmentIterator;
import seeding.ai_db_updater.handlers.AssignmentSAXHandler;
import seeding.Constants;
import seeding.ai_db_updater.handlers.TransactionSAXHandler;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateAssignmentData {

    public static void main(String[] args) {
        AssignmentIterator iterator = Constants.DEFAULT_ASSIGNMENT_ITERATOR;
        iterator.applyHandlers(new TransactionSAXHandler(), new AssignmentSAXHandler());
        System.out.println("Finished");
    }
}
