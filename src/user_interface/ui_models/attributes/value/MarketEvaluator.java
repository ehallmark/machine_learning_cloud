package user_interface.ui_models.attributes.value;

import seeding.Database;
import tools.DateHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Evan on 1/27/2017.
 */
public class MarketEvaluator extends ValueAttr {
    static final File transactionValueModelFile = new File("transaction_value_model.jobj");
    static final File assetFamilyValueModelFile = new File("asset_family_value_model.jobj");
    static final File maintenanceFeeValueModelFile = new File("maintenance_fee_value_model.jobj");

    private static final File[] files = new File[]{
            transactionValueModelFile,
            assetFamilyValueModelFile,
            maintenanceFeeValueModelFile
    };

    public MarketEvaluator() {
        super(ValueMapNormalizer.DistributionType.Normal,"Market Value");
    }

    @Override
    protected List<Map<String,Double>> loadModels() {
        return Arrays.stream(files).map(file->((Map<String,Double>)Database.tryLoadObject(file))).collect(Collectors.toList());
    }

    private static Map<File,Map<String,Double>> runModel(){
        Map<File,Map<String,Double>> fileToModelMaps = new HashMap<>();
        System.out.println("Starting to load market evaluator...");
        List<String> patents = new ArrayList<>(Database.getValuablePatents());
        Collection<String> assignees = Database.getAssignees();
        LocalDate earliestDate = LocalDate.now().minusYears(20);
        double earlyTrend = (new Double(earliestDate.getYear())+new Double(earliestDate.getMonthValue()-1.0)/12);
        System.out.println("Maintenance fee Model...");
        Map<String,Double> maintenanceFeeModel = new HashMap<>();
        {
            // maintenance fee reminders
            Map<String,LocalDate> patentToPubDateMap = (Map<String,LocalDate>)Database.tryLoadObject(new File("patent_to_pubdate_map_file.jobj"));
            Map<String,Integer> maintenanceReminderCountMap = (Map<String,Integer>)Database.tryLoadObject(new File("patent_to_fee_reminder_count_map.jobj"));
            patents.forEach(patent->{
                double score = 0.0;
                if(maintenanceReminderCountMap.containsKey(patent)) {
                    // subtract score since it is bad
                    score-=(double)(maintenanceReminderCountMap.get(patent));
                }
                // standardize score by date
                if(patentToPubDateMap.containsKey(patent)) {
                    LocalDate patentDate = patentToPubDateMap.get(patent);
                    score += ((new Double(patentDate.getYear())+new Double(patentDate.getMonthValue()-1.0)/12) - earlyTrend) / 5.0;
                }
                System.out.println("Maintenance score for "+patent+": "+score);
                maintenanceFeeModel.put(patent,score);
            });
        }

        System.out.println("Transaction Model...");
        Map<String,Double> transactionModel = new HashMap<>();
        {
            // Formula: sum(transaction/size(transaction))-security interests
            final File patentToSecurityInterestCountMapFile = new File("patent_to_security_interest_count_map.jobj");
            Map<String,Integer> patentToSecurityInterestCountMap = (Map<String,Integer>)Database.tryLoadObject(patentToSecurityInterestCountMapFile);
            final File patentToTransactionSizesMapFile = new File("patent_to_transaction_sizes_map.jobj");
            Map<String,List<Integer>> patentToTransactionSizeMap = (Map<String,List<Integer>>)Database.tryLoadObject(patentToTransactionSizesMapFile);
            patents.forEach(patent->{
                double score = 0.0;
                if(patentToTransactionSizeMap.containsKey(patent)) {
                    List<Integer> transactionSizes = patentToTransactionSizeMap.get(patent);
                    for(Integer transactionSize : transactionSizes) {
                        if(transactionSize>0) {
                            score += (1.0 / Math.log(1.0+transactionSize));
                        }
                    }
                }
                if(patentToSecurityInterestCountMap.containsKey(patent)) {
                    score-=patentToSecurityInterestCountMap.get(patent);
                }
                System.out.println("Transaction score for patent "+patent+": "+score);
                transactionModel.put(patent,score);
            });
        }

        System.out.println("Asset family model...");
        Map<String,Double> assetFamilyModel = new HashMap<>();
        {
            Map<String,Set<String>> relatedDocsMap = (Map<String,Set<String>>)Database.tryLoadObject(new File("patent_to_related_docs_map_file.jobj"));
            relatedDocsMap.forEach((patent,relatedAssets)->{
                if(relatedAssets.size()>0) {
                    double score = Math.log(relatedAssets.size());
                    for (String asset : relatedAssets) {
                        if (transactionModel.containsKey(asset)) {
                            score += transactionModel.get(asset);
                        }
                        if (maintenanceFeeModel.containsKey(asset)) {
                            score += maintenanceFeeModel.get(asset);
                        }
                    }
                    System.out.println("Family score for patent " + patent + ": " + score);
                    assetFamilyModel.put(patent, score);
                }
            });
        }


        DateHelper.addScoresToAssigneesFromPatents(assignees, assetFamilyModel);
        DateHelper.addScoresToAssigneesFromPatents(assignees, maintenanceFeeModel);
        DateHelper.addScoresToAssigneesFromPatents(assignees, transactionModel);

        fileToModelMaps.put(assetFamilyValueModelFile,assetFamilyModel);
        fileToModelMaps.put(maintenanceFeeValueModelFile,maintenanceFeeModel);
        fileToModelMaps.put(transactionValueModelFile,transactionModel);

        System.out.println("Finished evaluator...");
        return fileToModelMaps;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting to run model.");
        Map<File,Map<String,Double>> maps = runModel();
        System.out.println("Finished... Now writing model to file...");
        maps.forEach((file,map)->{
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                oos.writeObject(map);
                oos.flush();
                oos.close();
                System.out.println("Finished successfully.");
            }catch(Exception e) {
                e.printStackTrace();
            }
        });
    }
}
