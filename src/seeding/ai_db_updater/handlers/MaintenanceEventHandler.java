package seeding.ai_db_updater.handlers;

import org.nd4j.linalg.primitives.Triple;

import java.util.function.Consumer;

/**
 * Created by ehallmark on 7/12/17.
 */
public class MaintenanceEventHandler implements LineHandler {
    private Consumer<Triple<String,String,String>> postgresConsumer;
    public MaintenanceEventHandler(Consumer<Triple<String,String,String>> postgresConsumer) {
        this.postgresConsumer=postgresConsumer;
    }

    @Override
    public void handleLine(String line) {
        if (line.length() >= 57) {
            String appNum = line.substring(14, 22);
            String entity = line.substring(23, 24);
            String maintenanceCode = line.substring(52, 57).trim();
            postgresConsumer.accept(new Triple<>("US"+appNum,entity,maintenanceCode));
        }
    }

    @Override
    public void save() {

    }

}
