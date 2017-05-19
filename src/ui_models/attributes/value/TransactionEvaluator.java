package ui_models.attributes.value;

import seeding.Database;
import tools.DateHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Evan on 1/27/2017.
 */
public class TransactionEvaluator extends ValueAttr {
    public TransactionEvaluator() {
        super(ValueMapNormalizer.DistributionType.Uniform,"Transaction Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.asList((Map<String,Double>)Database.tryLoadObject(MarketEvaluator.transactionValueModelFile));
    }

}
