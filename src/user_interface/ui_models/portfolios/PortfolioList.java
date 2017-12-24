package user_interface.ui_models.portfolios;

import elasticsearch.DataSearcher;
import lombok.Getter;
import lombok.Setter;
import model.nodes.FactorNode;
import org.nd4j.linalg.primitives.Pair;
import user_interface.server.SimilarPatentServer;
import user_interface.ui_models.charts.tables.DeepList;
import user_interface.ui_models.portfolios.items.Item;

import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by ehallmark on 8/1/16.
 */
public class PortfolioList implements Comparable<PortfolioList> {
    @Getter @Setter
    private List<Item> itemList;
    private double avgSimilarity;
    public enum Type { patents, applications }
    private boolean init = false;

    public PortfolioList(List<Item> itemList) {
        this.itemList=itemList;
    }

    @Override
    public int compareTo(PortfolioList o) {
        return Double.compare(o.avgSimilarity,avgSimilarity);
    }

    public Stream<Pair<String,PortfolioList>> groupedBy(List<String> fields) {
        if(fields==null||fields.isEmpty()) return Stream.of(new Pair<>("",this));

        if(itemList.size()==0) return Stream.empty();

        String[] attrsArray = fields.toArray(new String[]{});
        List<Pair<Item,DeepList<Object>>> items = (List<Pair<Item,DeepList<Object>>>)itemList.stream().flatMap(item-> {
            List<List<?>> rs = fields.stream().map(attribute-> {
                Object r = item.getData(attribute);
                if (r != null) {
                    if (r instanceof Collection) {
                        return (List<?>) ((Collection)r).stream().collect(Collectors.toList());
                    } else if (r.toString().contains(DataSearcher.ARRAY_SEPARATOR)) {
                        return Arrays.asList(r.toString().split(DataSearcher.ARRAY_SEPARATOR));
                    } else {
                        return Collections.singletonList(r);
                    }
                }
                return Collections.emptyList();
            }).collect(Collectors.toList());
            FactorNode factor = new FactorNode(null,attrsArray,rs.stream().mapToInt(r->Math.max(1,r.size())).toArray());
            return factor.assignmentPermutationsStream().map(assignment->{
                return new Pair<>(item,
                        new DeepList<>(
                                IntStream.range(0,assignment.length).mapToObj(i->{
                                    if(i>=rs.size()) System.out.println("WARNING 1: "+factor.toString());
                                    List<?> r = rs.get(i);
                                    return r.size()>0?r.get(assignment[i]):"";
                                }).collect(Collectors.toList())
                        )
                );
            });
        }).collect(Collectors.toList());

        if(items.isEmpty()) return Stream.empty();

        System.out.println("Starting to group table...");

        return items.stream()
                .collect(Collectors.groupingBy(t->t.getSecond(),Collectors.mapping(pair->pair.getFirst(), Collectors.toList())))
                .entrySet()
                .stream().map(e->{
                    StringJoiner sj = new StringJoiner("| ");
                    for(int i = 0; i < fields.size(); i++) {
                        if(i>=e.getKey().size()) System.out.println("WARNING 2: "+e.getKey()+"  ->  "+fields);
                        sj.add(String.join(": ", SimilarPatentServer.fullHumanAttributeFor(fields.get(i)),e.getKey().get(i).toString()));
                    }
                    return new Pair<>(sj.toString(),new PortfolioList(e.getValue()));
                });
    }

    public void init(String sortedBy, int limit) {
        if(!init) {
            itemList = itemList.parallelStream().sorted((i1,i2)-> (Double.compare(((Number) (i2.getData(sortedBy))).doubleValue(), ((Number) (i1.getData(sortedBy))).doubleValue()))).collect(Collectors.toList());
            if (itemList.size() > 0) {
                itemList = itemList.subList(0,Math.min(itemList.size(), limit));
                //this.avgSimilarity = itemList.parallelStream().collect(Collectors.averagingDouble(obj -> obj.getSimilarity()));
            } else this.avgSimilarity = 0.0d;
        }
        init=true;
    }

    public String[] getIds() {
        return itemList.stream().map(item->item.getName()).toArray(size->new String[size]);
    }
}
