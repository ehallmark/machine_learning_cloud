package tools;


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
    public TreeGui(TreeNode<T> tree, int width, int height, int depth) {
        this.width=width;
        this.height=height;
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
        graphics.setFont(new Font("Tahoma", Font.BOLD, 20));
        RenderTree(graphics, 0, getWidth(), 0, getHeight() / depth, tree);
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


    public void RenderTree(Graphics g, int StartWidth, int EndWidth, int StartHeight, int Level, TreeNode<T> node) {
        String data = String.valueOf(node.data);
        g.setFont(new Font("Tahoma", Font.BOLD, 20));
        FontMetrics fm = g.getFontMetrics();
        int dataWidth = fm.stringWidth(data);
        g.drawString(data, (StartWidth + EndWidth) / 2 - dataWidth / 2, StartHeight + Level / 2);

        if(node.getChildren().isEmpty()) return;

        int interval = (EndWidth-StartWidth)/node.getChildren().size();
        int idx = 0;
        for(TreeNode<T> child : node.getChildren()) {
            RenderTree(g, StartWidth+(idx*interval), StartWidth+((idx+1)*interval), StartHeight + Level, Level, child);
            idx++;
        }

    }

}



