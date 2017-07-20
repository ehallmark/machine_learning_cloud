package seeding.ai_db_updater;

import seeding.Constants;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateTermAdjustmentData {

    public static void main(String[] args) {
        LineHandler handler = new TermAdjustmentHandler();
        File dataFolder = new File("data/patent_term_adjustments/");
        Arrays.stream(dataFolder.listFiles()).parallel().forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                reader.lines().parallel().forEach(line->{
                    handler.handleLine(line);
                });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                file.delete();
            }
        });
        handler.save();
    }
}
