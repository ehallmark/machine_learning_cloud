package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 7/12/17.
 */
public interface LineHandler {
    void save();
    void handleLine(String line);
}
