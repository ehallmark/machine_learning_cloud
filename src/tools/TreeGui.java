package tools;


import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

public class TreeGui<T> extends JFrame {

    private JPanel contentPane;
    public TreeNode<T> tree;
    public DrawTree<T> drawer;

    /**
     * Create the frame.
     */
    public TreeGui(TreeNode<T> tree, int depth) {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setBounds(100, 100, 500, 500);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        contentPane.setLayout(new BorderLayout(0, 0));
        drawer = new DrawTree<>(tree,depth);

        contentPane.add(drawer);
        setContentPane(contentPane);
        this.tree = tree;
        setVisible(true);
    }

}

class DrawTree<T> extends JPanel{

    private TreeNode<T> tree;
    private int depth;

    public DrawTree(TreeNode<T> tree, int depth){
        this.tree = tree;
        this.depth=depth;
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setFont(new Font("Tahoma", Font.BOLD, 20));
        RenderTree(g, 0, getWidth(), 0, getHeight() / depth, tree);

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

