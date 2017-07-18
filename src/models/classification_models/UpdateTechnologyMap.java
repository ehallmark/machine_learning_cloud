package models.classification_models;

import seeding.Database;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 7/18/17.
 */
public class UpdateTechnologyMap {
    public static void main(String[] args) {
        Map<String,String> assetToTechnologyMap = Collections.synchronizedMap(new HashMap<>());
        ClassificationAttr tagger = TechTaggerNormalizer.getDefaultTechTagger();
        Database.getAllPatentsAndApplications().parallelStream().forEach(asset->{
            String tech = tagger.attributesFor(Arrays.asList(asset),1).stream().map(p->p.getFirst()).findAny().orElse(null);
            if(tech!=null&&tech.length()>0) {
                assetToTechnologyMap.put(asset,tech);
            }
        });
        Database.getAssignees().parallelStream().forEach(assignee->{
            String tech = tagger.attributesFor(Arrays.asList(assignee),1).stream().map(p->p.getFirst()).findAny().orElse(null);
            if(tech!=null&&tech.length()>0) {
                assetToTechnologyMap.put(assignee,tech);
            }
        });

        Database.trySaveObject(assetToTechnologyMap,Database.technologyMapFile);
    }
}
