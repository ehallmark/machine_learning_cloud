package seeding.ai_db_updater.handlers;

import org.xml.sax.helpers.DefaultHandler;

/**
 * Created by Evan on 7/5/2017.
 */
public abstract class CustomHandler extends DefaultHandler {
    public abstract void reset();
    public abstract void save();
    public abstract CustomHandler newInstance();
}
