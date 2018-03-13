package models.assignee.normalization.subsidiary;

import org.gephi.graph.api.Node;
import seeding.Database;
import visualization.Visualizer;

import java.awt.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DrawGraph {
    public static void main(String[] args) {
        final String modelName = "assignee_subsidiary_graph";
        Visualizer visualizer = new Visualizer(modelName);

        Map<String,String> childToParentMap = ParentSubsidiaryGraph.loadChildToParentMap();

        Set<String> allAssignees = Collections.synchronizedSet(new HashSet<>(Database.getAssignees()));
        allAssignees.addAll(Database.getNormalizedAssignees());

        childToParentMap = childToParentMap.entrySet().parallelStream().filter(e->allAssignees.contains(e.getValue()))
                .collect(Collectors.toMap(e->e.getKey(),e->e.getValue()));

        Set<String> matchedAssignees = new HashSet<>(childToParentMap.keySet());
        Map<String,Integer> assigneeToPortfolioSizeMap = matchedAssignees.parallelStream()
                .collect(Collectors.toMap(assignee->assignee,assignee->Math.max(Database.getNormalizedAssetCountFor(assignee),Database.getAssetCountFor(assignee))));

        System.out.println("Num assignees matched: "+childToParentMap.size());

        Map<String,Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger cnt = new AtomicInteger(0);
        childToParentMap.forEach((child,parent)->{
            if(child.equals(parent)) return;
            try {
                Node childNode = nodeMap.containsKey(child) ? nodeMap.get(child) : visualizer.addNode(child, Math.max(100f,1f+(float)Math.log(assigneeToPortfolioSizeMap.getOrDefault(child,1)))/100f, Color.RED);
                Node parentNode = nodeMap.containsKey(parent) ? nodeMap.get(parent) : visualizer.addNode(parent, Math.max(100f,1f+(float)Math.log(assigneeToPortfolioSizeMap.getOrDefault(parent,1)))/100f, Color.BLUE);
                if(!childNode.getColor().equals(Color.RED)) childNode.setColor(Color.RED);

                nodeMap.putIfAbsent(child, childNode);
                nodeMap.putIfAbsent(parent, parentNode);
                visualizer.addEdge(parentNode, childNode, 1f, Color.BLACK);
                if (cnt.getAndIncrement() % 1000 == 999) {
                    System.out.println("Completed: " + cnt.get());
                }
            } catch(Exception e) {
                System.out.println("Has child: "+nodeMap.containsKey(child)+"; Has parent: "+nodeMap.containsKey(parent));
                System.out.println("Parent: "+parent+"; Child: "+child);
            }
        });

        visualizer.save();
    }
}
