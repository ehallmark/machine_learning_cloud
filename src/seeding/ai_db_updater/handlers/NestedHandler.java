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

    public void init() {
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


    protected void resetAllDescendants(Flag root) {
        root.reset();
        if(!root.isLeaf()) {
            root.children.forEach(child->resetAllDescendants(child));
        }
    }

    public void startElement(String uri,String localName,String qName,
        Attributes attributes)throws SAXException{
        endFlags.forEach(flag->{
            startHelper(Arrays.asList(flag),localName, attributes);
        });
    }

    private void startHelper(List<Flag> list, String localName, Attributes attributes) {
        if(!list.isEmpty()) {
            list.forEach(flag->{
                if(flag.isAttributeFlag()) {
                    final String text = attributes.getValue(flag.localName);
                    if (text != null) {
                        Object value = flag.apply(text);
                        if(value != null && flag.validValue(value.toString())) {
                            flag.getEndFlag().getDataMap().put(flag, text);
                        }
                    }
                } else {
                    if(localName==null) {
                        System.out.println("LOCAL NAME WAS NULL!!!");
                        System.exit(1);
                    }
                    flag.setTrueIfEqual(localName);
                    if (flag.get()) {
                        startHelper(flag.children, localName, attributes);
                    }
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
            if(flag.compareTag(localName)) {
                flag.save();
                flag.resetDataMap();
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
                if(flag.get()) {
                    endHelper(flag.children, localName, shouldClear);
                    if (flag.compareTag(localName)) {
                        flag.reset();
                        if (flag.isLeaf()) {
                            final String text = String.join(" ", documentPieces).trim();
                            shouldClear.set(true);
                            Object value = flag.apply(text);
                            if (value != null && flag.validValue(value.toString())) {
                                flag.getEndFlag().getDataMap().put(flag, text);
                            }
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