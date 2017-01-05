

package tools;


import org.apache.xpath.operations.Bool;
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
    private final int width;
    private final int height;
    private final int depth;
    private static final int VERTICAL_PADDING = 15;
    private static final int MAX_WIDTH_FOR_TEXT = 200;
    private static final int HORIZONTAL_PADDING = 15;
    private static final int FONT_SIZE = 20;
    private static final int SUB_FONT_SIZE = 12;

    public TreeGui(TreeNode<T> tree, int depth, int k) {
        this.height=depth*150;
        this.width=(2*HORIZONTAL_PADDING+MAX_WIDTH_FOR_TEXT)*(int)Math.round(Math.pow(k,depth));
        this.depth=depth;
        this.tree = tree;
    }


    public void draw() {
        setBounds(0,0,width,height);
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = image.getGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("Tahoma", Font.BOLD, FONT_SIZE));
        RenderTree(graphics, 0, width, 0, height / depth, tree);
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

    public Pair<Point,Point> DrawHelper(TreeNode<T> node, Graphics g, int StartWidth, int EndWidth, int StartHeight, int Level) {
        String data = node.getData().toString();
        String[] lines = data.split("\\n");
        int maxDataWidth = 0;
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int FontSize = new int[]{FONT_SIZE,SUB_FONT_SIZE}[Math.min(i,1)];
            g.setFont(new Font("Tahoma", Font.BOLD, FontSize));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            while(dataWidth >= MAX_WIDTH_FOR_TEXT) {
                g.setFont(new Font("Tahoma", Font.BOLD, fm.getFont().getSize()-1));
                fm = g.getFontMetrics();
                dataWidth = fm.stringWidth(line);
            }
            maxDataWidth=Math.max(dataWidth,maxDataWidth);
        }

        int topY = StartHeight + Level/2 - (FONT_SIZE*lines.length)/2 - VERTICAL_PADDING;
        int bottomY = StartHeight + Level/2 + (FONT_SIZE*lines.length)/2 + VERTICAL_PADDING;
        int widthAvg = (StartWidth+EndWidth)/2;
        Point topConnection = new Point(widthAvg,topY);
        Point bottomConnection = new Point(widthAvg,bottomY);

        g.setColor(Color.CYAN);
        g.fillRect(topConnection.x-maxDataWidth/2-HORIZONTAL_PADDING,topConnection.y,maxDataWidth+2*HORIZONTAL_PADDING,bottomConnection.y-topConnection.y);
        g.setColor(Color.BLACK);
        g.drawRect(topConnection.x-maxDataWidth/2-HORIZONTAL_PADDING,topConnection.y,maxDataWidth+2*HORIZONTAL_PADDING,bottomConnection.y-topConnection.y);

        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int FontSize = new int[]{FONT_SIZE,SUB_FONT_SIZE}[Math.min(i,1)];
            g.setFont(new Font("Tahoma", Font.BOLD, FontSize));
            FontMetrics fm = g.getFontMetrics();
            int dataWidth = fm.stringWidth(line);
            while(dataWidth >= MAX_WIDTH_FOR_TEXT) {
                g.setFont(new Font("Tahoma", Font.BOLD, fm.getFont().getSize()-1));
                fm = g.getFontMetrics();
                dataWidth = fm.stringWidth(line);
            }
            g.drawString(line, widthAvg - dataWidth / 2, topY + i*FontSize + 2*VERTICAL_PADDING);
        }

        return new Pair<>(topConnection,bottomConnection);
    }

    // x coord, y coord, data width
    public Pair<Point,Point> RenderTree(Graphics g, int StartWidth, int EndWidth, int StartHeight, int Level, TreeNode<T> node) {
        Pair<Point,Point> coordinates = DrawHelper(node,g,StartWidth,EndWidth,StartHeight,Level);

        if(!node.getChildren().isEmpty()) {

            int interval = (EndWidth - StartWidth) / node.getChildren().size();
            int idx = 0;
            for (TreeNode<T> child : node.getChildren()) {
                // draw child
                Pair<Point, Point> childCoords = RenderTree(g, StartWidth + (idx * interval), StartWidth + ((idx + 1) * interval), StartHeight + Level, Level, child);
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

