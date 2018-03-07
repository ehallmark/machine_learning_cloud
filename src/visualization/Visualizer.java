package visualization;

import org.gephi.graph.api.*;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.spi.GraphExporter;
import org.gephi.layout.api.LayoutController;
import org.gephi.layout.api.LayoutModel;
import org.gephi.layout.plugin.AutoLayout;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.force.yifanHu.YifanHuProportional;
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.openide.util.Lookup;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Evan on 9/30/2017.
 */
public class Visualizer {
    private GraphModel graphModel;
    private Graph graph;
    private String name;
    public Visualizer() {
        this("data/technology-graph-gephi");
    }

    public Visualizer(String name) {
        this.name=name;

        setupWorkspace();

        //Get a graph model - it exists because we have a workspace
        graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();

        //Append as a Graph
        graph = graphModel.getGraph();
    }

    public Node addNode(String name, float weight, Color color) {
        Node n1 = graphModel.factory().newNode(name);
        n1.setLabel(name);
        n1.setColor(color);
        n1.setSize(weight);
        graph.addNode(n1);
        return n1;
    }

    public Edge addEdge(Node n1, Node n2, float weight, Color color) {
        //Create an edge - directed and weight 1
        Edge e1 = graphModel.factory().newEdge(n1, n2, 0, weight, false);
        e1.setColor(color);
        graph.addEdge(e1);
        return e1;
    }

    public void save() {
        // execute layout
        executeLayout();
        saveCurrentGraph(name);
    }

    private void executeLayout() {
        //Layout for 1 minute

        YifanHuLayout firstLayout = new YifanHuProportional().buildLayout();
        firstLayout.setGraphModel(graphModel);
        firstLayout.initAlgo();
        firstLayout.goAlgo();
        firstLayout.endAlgo();


    }

    private static void setupWorkspace() {
        //Init a project - and therefore a workspace
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        pc.getCurrentWorkspace();
    }

    private static void saveCurrentGraph(String fileName) {
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        //Export only visible graph
        GraphExporter exporter = (GraphExporter) ec.getExporter("gexf"); //Get GEXF exporter
        try {
            ec.exportFile(new File(fileName.replace(".gexf","")+".gexf"), exporter);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }
    }

    public static void main(String[] args) {
        Visualizer visualizer = new Visualizer();
        Color edgeColor = Color.BLACK;
        Node n1 = visualizer.addNode("n1",1f,Color.BLACK);
        Node n2 = visualizer.addNode("n2",2f,Color.GREEN);
        Node n3 = visualizer.addNode("n3",3f,Color.CYAN);
        Node n4 = visualizer.addNode("n4",5f,Color.BLUE);
        visualizer.addEdge(n1,n2,0.2f,edgeColor);
        visualizer.addEdge(n2,n3,0.5f,edgeColor);
        visualizer.addEdge(n4,n1,0.1f,edgeColor);
        visualizer.addEdge(n4,n2,0.3f,edgeColor);
        visualizer.save();
    }
}
