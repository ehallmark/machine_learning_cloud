package tools;


import org.deeplearning4j.berkeley.Pair;

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
    private static final int VERTICAL_PADDING = 15;
    private static final int MIN_FONT_SIZE = 12;
    private static final int HORIZONTAL_PADDING = 15;

    public TreeGui(TreeNode<T> tree, int depth, int k) {
        this.height=depth*300;
        this.width=100*(int)Math.round(Math.pow(k,depth));
        this.depth=depth;
        this.tree = tree;
    }


    public void draw() {
        setBounds(0,0,width,height);
        int FontSize = 22;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Tahoma", Font.BOLD, FontSize));
        RenderTree(graphics, FontSize, 0, width, 0, height / depth, tree);
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

    public Pair<Point,Point> DrawHelper(TreeNode<T> node, Graphics g, int StartWidth, int EndWidth, int StartHeight, int Level, int FontSize) {
        String data = node.getData().toString();
        String[] lines = data.split("\\n");
        int maxDataWidth = 0;
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            g.setFont(new Font("Tahoma", Font.BOLD, Math.max(MIN_FONT_SIZE,FontSize-(i))));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            maxDataWidth=Math.max(dataWidth,maxDataWidth);
        }
        Point topConnection = new Point((StartWidth+EndWidth)/2,StartHeight + Level/2 - (FontSize*lines.length)/2 - VERTICAL_PADDING);
        Point bottomConnection = new Point(topConnection.x,StartHeight + Level/2 + (FontSize*lines.length)/2 + VERTICAL_PADDING);

        g.setColor(Color.CYAN);
        g.fillRect(topConnection.x-maxDataWidth/2-HORIZONTAL_PADDING,topConnection.y,maxDataWidth+2*HORIZONTAL_PADDING,bottomConnection.y-topConnection.y);
        g.setColor(Color.BLACK);
        g.drawRect(topConnection.x-maxDataWidth/2-HORIZONTAL_PADDING,topConnection.y,maxDataWidth+2*HORIZONTAL_PADDING,bottomConnection.y-topConnection.y);

        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            g.setFont(new Font("Tahoma", Font.BOLD, FontSize-(2*i)));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            g.drawString(line, (StartWidth + EndWidth) / 2 - dataWidth / 2, (StartHeight + Level / 2) + i*FontSize);
        }

        return new Pair<>(topConnection,bottomConnection);
    }

    // x coord, y coord, data width
    public Pair<Point,Point> RenderTree(Graphics g, int FontSize, int StartWidth, int EndWidth, int StartHeight, int Level, TreeNode<T> node) {

        Pair<Point,Point> coordinates = DrawHelper(node,g,StartWidth,EndWidth,StartHeight,Level,FontSize);

        if(!node.getChildren().isEmpty()) {

            int interval = (EndWidth - StartWidth) / node.getChildren().size();
            int idx = 0;
            for (TreeNode<T> child : node.getChildren()) {
                // draw child
                Pair<Point, Point> childCoords = RenderTree(g, Math.max(MIN_FONT_SIZE,FontSize-1), StartWidth + (idx * interval), StartWidth + ((idx + 1) * interval), StartHeight + Level, Level, child);
                if (childCoords != null) {
                    // draw lines
                    g.drawLine(coordinates.getSecond().x,coordinates.getSecond().y,childCoords.getFirst().x,childCoords.getFirst().y);
                }
                idx++;
            }
        }

        return coordinates;
    }

}



