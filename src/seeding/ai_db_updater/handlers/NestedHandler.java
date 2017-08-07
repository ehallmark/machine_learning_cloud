package seeding.ai_db_updater.handlers;

/**
 * Created by ehallmark on 1/3/17.
 */

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import seeding.Database;
import seeding.ai_db_updater.handlers.flags.EndFlag;
import seeding.ai_db_updater.handlers.flags.Flag;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**

 */
public abstract class NestedHandler extends CustomHandler{
    protected final List<String> documentPieces = new ArrayList<>();
    // flags where data exists
    private final List<Flag> leafFlags = new ArrayList<>();
    // flags that indicate a change in object(s)
    protected final List<EndFlag> endFlags = new ArrayList<>();
    public NestedHandler() {
        initAndAddFlagsAndEndFlags();
        setLeafs();
    }

    protected abstract void initAndAddFlagsAndEndFlags();

    protected void setLeafs() {
        endFlags.forEach(root->{
            setLeafsHelper(root,leafFlags);
        });
    }

    protected void setLeafsHelper(Flag root, List<Flag> list) {
        if(root.isLeaf()) {
            list.add(root);
        } else {
            root.children.forEach(child->setLeafsHelper(child,list));
        }
    }

    @Override
    public void reset() {
        endFlags.forEach(flag->{
            resetAllDescendants(flag);
        });
    }

    protected void resetAllDescendants(Flag root) {
        root.reset();
        if(!root.isLeaf()) {
            root.children.forEach(child->resetAllDescendants(child));
        }
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        endFlags.forEach(flag->{
            startHelper(Arrays.asList(flag),localName);
        });
    }

    private void startHelper(List<Flag> list, String localName) {
        if(!list.isEmpty()) {
            list.forEach(item->{
                item.setTrueIfEqual(localName);
                if(item.get()) {
                    startHelper(item.children, localName);
                }
            });
        }
    }

    public void endElement(String uri,String localName,
        String qName)throws SAXException{
        AtomicBoolean shouldClear = new AtomicBoolean(false);
        endFlags.forEach(flag->{
            endHelper(Arrays.asList(flag),localName, shouldClear);
        });

        // check end flags
        endFlags.forEach(flag->{
            if(flag.localName.equals(localName)) {
                flag.save();
                resetAllDescendants(flag);
            }
        });

        // check if we need to clear documents
        if(shouldClear.get()) {
            documentPieces.clear();
        }
    }


    private void endHelper(List<Flag> flags, String localName, AtomicBoolean shouldClear) {
        if(!flags.isEmpty()) {
            flags.forEach(flag->{
                endHelper(flag.children,localName,shouldClear);
                if(flag.compareTag(localName)) {
                    flag.reset();
                    if(flag.isLeaf()) {
                        final String text = String.join("", documentPieces).trim();
                        shouldClear.set(true);
                        if(flag.validValue(text)) {
                            flag.getEndFlag().getDataMap().put(flag,text);
                        }
                    }
                }
            });
        }
    }

    public void characters(char ch[],int start,int length)throws SAXException{
        if(leafFlags.stream().anyMatch(flag->flag.get())){
            documentPieces.add(new String(ch,start,length));
        }
    }
}