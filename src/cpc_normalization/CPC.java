package cpc_normalization;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import models.keyphrase_prediction.MultiStem;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Created by Evan on 10/24/2017.
 */
public class CPC implements Serializable {
    private static final long serialVersionUID = 1L;
    @Getter
    private Set<CPC> children;
    @Getter @Setter
    private CPC parent;
    @Getter
    private String name;
    @Getter
    private String[] parts;
    @Getter
    private int numParts;
    @Setter @Getter
    private Set<MultiStem> keywords;
    public CPC(@NonNull String name) {
        this.name=name;
        this.children = Collections.synchronizedSet(new HashSet<>());
        this.parts = cpcToParts(name);
        this.numParts = (int) Stream.of(parts).filter(p->p!=null).count();
    }

    public int numSubclasses() {
        if(children.isEmpty()) {
            return 0;
        } else {
            return children.stream().mapToInt(child->child.numSubclasses()).sum();
        }
    }

    @Override
    public boolean equals(Object other) {
        if(other==null)return false;
        return this.toString().equals(other.toString());
    }

    @Override
    public int hashCode() {
        if(name==null) {
            return super.hashCode();
        }
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }

    public void addChild(CPC child) {
        children.add(child);
    }

    public static String[] cpcToParts(String cpc) {
        String[] parts = new String[5];
        if(cpc.endsWith("/00")) cpc=cpc.substring(0,cpc.length()-3);
        if(cpc.length()>=1) parts[0] = cpc.substring(0,1);
        if(cpc.length()>=3) parts[1] = cpc.substring(1,3);
        if(cpc.length()>=4) parts[2] = cpc.substring(3,4);
        if(cpc.length()>4) {
            String groupStr = cpc.substring(4);
            String[] groups = groupStr.split("/");
            for(int i = 0; i < groups.length; i++) {
                parts[3+i] = groups[i].trim();
            }
        }
        return parts;
    }

    public boolean isParentOf(CPC child) {
        String[] parentParts = parts;
        String[] childParts = child.parts;

        if(numParts!=child.numParts-1) return false;

        boolean same = true;
        for(int i = 0; i < 5; i++) {
            if(parentParts[i]==null||childParts[i]==null) break;
            if(!parentParts[i].equals(childParts[i])) {
                same = false;
                break;
            }
        }

        return same;
    }
}
