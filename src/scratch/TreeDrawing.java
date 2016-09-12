package scratch;

import tools.TreeGui;
import tools.TreeNode;

/**
 * Created by ehallmark on 9/12/16.
 */
public class TreeDrawing {
    public static void main(String[] args) {
        TreeNode<String> root = new TreeNode<>("root");
        TreeNode<String> n1 = root.addChild("n1");
        TreeNode<String> n2 = root.addChild("n2");
        n1.addChild("n1_1");
        n1.addChild("n1_2");
        n1.addChild("n1_3");
        n2.addChild("n2_1");
        TreeGui<String> gui = new TreeGui<>(root,3);
        gui.setVisible(true);
    }
}
