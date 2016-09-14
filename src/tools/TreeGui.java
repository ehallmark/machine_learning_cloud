package tools;


import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.berkeley.Triple;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;


public class TreeGui<T> extends Panel {

    private TreeNode<T> tree;
    private BufferedImage image;
    private int width;
    private int height;
    private int depth;

    /**
     * Create the frame.
     */
    public TreeGui(TreeNode<T> tree, int depth, int k) {
        this.height=depth*200;
        this.width=depth*k*400;
        this.depth=depth;
        this.tree = tree;
    }


    public void draw() {
        setBounds(0,0,width,height);
        int fontSize = 18;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Tahoma", Font.BOLD, fontSize));
        RenderTree(graphics, fontSize, 0, width, 0, height / depth, tree);
        //paint(graphics);
    }

    public boolean writeToOutputStream(OutputStream os) {
        if(image==null) throw new RuntimeException("Please call draw writing to output stream!");
        boolean success = false;
        try {
            ImageIO.write(image, "gif", os);
            success = true;
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace(System.err);
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }
        return success;
    }

    // x coord, y coord, data width
    public Triple<Integer,Integer,Integer> RenderTree(Graphics g, int fontSize, int StartWidth, int EndWidth, int StartHeight, int Level, TreeNode<T> node) {
        String data = node.getData().toString();
        String[] lines = data.split("\\n");
        int maxDataWidth = 0;
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            g.setFont(new Font("Tahoma", Font.BOLD, fontSize-(2*i)));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            maxDataWidth=Math.max(dataWidth,maxDataWidth);
        }

        Triple<Integer,Integer,Integer> coords = new Triple<>((StartWidth + EndWidth) / 2 - maxDataWidth / 2,StartHeight + Level / 2, maxDataWidth);
        g.setColor(Color.CYAN);
        g.fillOval(coords.getFirst()-maxDataWidth/2,coords.getSecond()-2*fontSize,2*maxDataWidth,3*fontSize*lines.length);
        g.setColor(Color.BLACK);
        g.drawOval(coords.getFirst()-maxDataWidth/2,coords.getSecond()-2*fontSize,2*maxDataWidth,3*fontSize*lines.length);

        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            g.setFont(new Font("Tahoma", Font.BOLD, fontSize-(2*i)));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            g.drawString(line, (StartWidth + EndWidth) / 2 - dataWidth / 2, (StartHeight + Level / 2) + i*fontSize);
        }

        if(!node.getChildren().isEmpty()) {

            int interval = (EndWidth - StartWidth) / node.getChildren().size();
            int idx = 0;
            for (TreeNode<T> child : node.getChildren()) {
                // draw child
                Triple<Integer, Integer, Integer> childCoords = RenderTree(g, fontSize-1, StartWidth + (idx * interval), StartWidth + ((idx + 1) * interval), StartHeight + Level, Level, child);
                if (childCoords != null) {
                    // draw lines
                    g.drawLine(childCoords.getFirst()+(childCoords.getThird()/2), childCoords.getSecond()-child.getData().toString().split("\\n").length*fontSize, coords.getFirst()+(maxDataWidth/2), coords.getSecond()-2*fontSize+3*lines.length*fontSize);
                }
                idx++;
            }
        }

        return coords;
    }

}



