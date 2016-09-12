package tools;

import analysis.Classification;
import analysis.KMeansCalculator;
import analysis.Patent;
import org.deeplearning4j.berkeley.Pair;

import java.util.*;

/**
 * Created by ehallmark on 9/11/16.
 */
public class TreeNode<T> {

    T data;
    TreeNode<T> parent;
    List<TreeNode<T>> children;

    public TreeNode(T data) {
        this.data = data;
        this.children = new ArrayList<>();
    }

    public TreeNode<T> addChild(T child) {
        TreeNode<T> childNode = new TreeNode<>(child);
        childNode.parent = this;
        this.children.add(childNode);
        return childNode;
    }

    public List<TreeNode<T>> getChildren() {
        return children;
    }

    public T getData() {
        return data;
    }

    public TreeNode<T> getParent() {
        return parent;
    }


}