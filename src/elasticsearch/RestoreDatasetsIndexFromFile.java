package elasticsearch;

import seeding.Database;

import java.io.File;
import java.util.Map;

public class RestoreDatasetsIndexFromFile {
    public static void main(String[] args) {
        File backupFile = BackupDatasetsIndexToFile.backupFile;

        Map<String,Map<String,Object>> data = (Map<String,Map<String,Object>>) Database.tryLoadObject(backupFile);

        data.forEach(DatasetIndex::index);

    }
}
