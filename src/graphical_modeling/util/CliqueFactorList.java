package graphical_modeling.util;

import graphical_modeling.model.nodes.CliqueNode;
import graphical_modeling.model.nodes.FactorNode;
import graphical_modeling.model.nodes.Node;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

/**
 * Created by ehallmark on 4/27/17.
 *
 * This class is meant to solve the issue in the current clique tree
 *  implementation where the factorSet (from the perspective of the clique tree)
 *  is not linked to the actual factors belonging to each clique node.
 */
public class CliqueFactorList implements List<FactorNode> {
    private List<CliqueNode> nodes;
    public CliqueFactorList(List<Node> cliques) {
        nodes=cliques.stream().map(n->(CliqueNode)n).collect(Collectors.toList());
    }

    @Override
    public int size() {
        return nodes.size();
    }

    @Override
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return (indexOf(o) >= 0);
    }

    @Override
    public Iterator<FactorNode> iterator() {
        return nodes.stream().map(n->n.getCliqueFactor()).iterator();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(FactorNode factorNode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return c.stream().allMatch(o->contains(0)); // pretty slow so don't use
    }

    @Override
    public boolean addAll(Collection<? extends FactorNode> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends FactorNode> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactorNode get(int index) {
        return nodes.get(index).getCliqueFactor();
    }

    @Override
    public FactorNode set(int index, FactorNode element) {
        nodes.get(index).setCliqueFactor(element);
        return element;
    }

    @Override
    public void add(int index, FactorNode element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FactorNode remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int indexOf(Object o) {
        for(int i = 0; i < nodes.size(); i++) {
            if(nodes.get(i).getCliqueFactor().equals(o)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<FactorNode> listIterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListIterator<FactorNode> listIterator(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<FactorNode> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }
}
