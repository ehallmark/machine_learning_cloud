package graphical_modeling.model.nodes;

import graphical_modeling.model.functions.normalization.DivideByPartition;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by ehallmark on 4/25/17.
 */
public class CliqueNode extends Node {
    private static final long serialVersionUID = 1l;
    protected Collection<Node> nodes;
    @Getter
    public final Set<String> nameSet;
    transient protected Map<String,FactorNode> incomingMessageMap;
    @Getter
    @Setter
    protected FactorNode cliqueFactor;

    public CliqueNode(Collection<Node> nodes) {
        super(null,nodes.size());
        this.nodes=nodes;
        this.nameSet=new HashSet<>();
        this.incomingMessageMap=new HashMap<>();
        nodes.forEach(node->nameSet.add(node.getLabel()));
        // For Clique nodes use the CliqueFactor, which insures the has one relationship
        this.factors=Collections.unmodifiableList(Collections.EMPTY_LIST);
    }

    public CliqueNode() {
        this(new ArrayList<>());
    }

    public void addNode(Node node) {
        if(!this.nameSet.contains(node.getLabel())) {
            this.nodes.add(node);
            this.nameSet.add(node.getLabel());
        }
        cardinality=this.nodes.size();
    }

    @Override
    public void addFactor(FactorNode factor) {
        throw new UnsupportedOperationException("Use setCliqueFactor instead.");
    }

    // Incorporate incoming messages
    protected void receiveMessage(FactorNode message, Node sendingNode) {
        incomingMessageMap.put(sendingNode.getLabel(),message);
    }

    public void incorporateMessagesIntoFactor() {
        AtomicReference<FactorNode> factorRef = new AtomicReference<>(cliqueFactor);
        incomingMessageMap.forEach((senderLabel,message)->{
            factorRef.set(factorRef.get().multiply(message));
        });
        cliqueFactor=factorRef.get();
        cliqueFactor.reNormalize(new DivideByPartition());
        //incomingMessageMap.clear(); // Don't need them anymore?
    }

    // Send messages upstream (1st pass)
    public void prepAndSendMessageToParent() {
        if(this.getParents().size()>1) throw new RuntimeException("Invalid tree");
        this.getParents().forEach(parent->{
            FactorNode message = getMessageFor((CliqueNode)parent);
            ((CliqueNode)parent).receiveMessage(message,this);
        });
    }

    // Send messages downstream (2nd pass)
    public void prepAndSendMessagesToChildren() {
        this.getChildren().forEach(child->{
            FactorNode message = getMessageFor((CliqueNode)child);
            ((CliqueNode)child).receiveMessage(message,this);
        });
    }

    // Gets the message to be passed from this -> other
    public FactorNode getMessageFor(CliqueNode otherNode) {
        AtomicReference<FactorNode> newFactorRef = new AtomicReference<>(cliqueFactor);
        incomingMessageMap.forEach((senderLabel,message)->{
            if(!otherNode.getLabel().equals(senderLabel)) {
                newFactorRef.set(newFactorRef.get().multiply(message));
            }
        });
        // need to sum out variables
        FactorNode newFactor = newFactorRef.get();

        List<String> toSumOver = new ArrayList<>();
        nameSet.forEach(label->{
            if(!otherNode.getNameSet().contains(label)) toSumOver.add(label);
        });

        FactorNode result = newFactor.sumOut(toSumOver.toArray(new String[toSumOver.size()]));

        result.reNormalize(new DivideByPartition());
        return result;
    }

    public boolean hasFactorScope(String[] varLabels) {
        return Arrays.stream(varLabels).allMatch(label->nameSet.contains(label));
    }
}
