package models.assignee.normalization.subsidiary;

import org.gephi.graph.api.Node;
import visualization.Visualizer;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class DrawGraph {
    public static void main(String[] args) {
        final String modelName = "assignee_subsidiary_graph";
        Visualizer visualizer = new Visualizer(modelName);

        Map<String,String> childToParentMap = ParentSubsidiaryGraph.loadChildToParentMap();

        Map<String,Node> nodeMap = Collections.synchronizedMap(new HashMap<>());
        AtomicInteger cnt = new AtomicInteger(0);
        childToParentMap.forEach((child,parent)->{
            if(child.equals(parent)) return;
            try {

                Node childNode = nodeMap.containsKey(child) ? nodeMap.get(child) : visualizer.addNode(child, 2f, Color.RED);
                Node parentNode = nodeMap.containsKey(parent) ? nodeMap.get(parent) : visualizer.addNode(parent, 4f, Color.BLUE);
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
