package seeding.ai_db_updater;

import seeding.Constants;
import seeding.ai_db_updater.handlers.*;
import seeding.ai_db_updater.iterators.PatentGrantIterator;
import user_interface.ui_models.portfolios.PortfolioList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Evan on 1/22/2017.
 */
public class UpdateTermAdjustmentData {

    public static void main(String[] args) {
        LineHandler handler = new TermAdjustmentHandler();
        File dataFolder = new File("data/patent_term_adjustments/");
        Arrays.stream(dataFolder.listFiles()).forEach(file -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                AtomicInteger cnt = new AtomicInteger(0);
                reader.lines().parallel().forEach(line->{
                    if(cnt.getAndIncrement()%10000==0) System.out.println("Cnt: "+cnt.get());
                    handler.handleLine(line);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        handler.save();
    }
}
