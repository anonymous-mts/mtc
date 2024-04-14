package verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.MutableGraph;
import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import graph.Edge;
import graph.EdgeType;
import graph.MatrixGraph;
import history.Event;
import history.History;
import history.Transaction;
import history.Event.EventType;
import monosat.Lit;
import monosat.Logic;
import monosat.Solver;

class Utils {
    public static <KeyType,ValueType> boolean verifyInternalConsistency(Transaction<KeyType, ValueType> txn) {
        var events = txn.getEvents();
        var key2value = new HashMap<>();
        for (var event : events) {
            if (event.getType() == EventType.WRITE) {
                // 写操作，更新值
                key2value.put(event.getKey(), event.getValue());
            } else {
                if (!key2value.containsKey(event.getKey())) {
                    // 首次读，更新值
                    key2value.put(event.getKey(), event.getValue());
                } else {
                    // 非首次读，检查值是否一致
                    var value = key2value.get(event.getKey());
                    if (!event.getValue().equals(value)) {
                        // 不一致
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static <KeyType,ValueType> void printTransaction(Transaction<KeyType, ValueType> txn) {
        System.out.println(txn);
        for (var event : txn.getEvents()) {
            System.out.println("\t" + event);
        }
    }

    public static <KeyType,ValueType>  void printCounterExample(MutableGraph<Transaction<KeyType, ValueType>> graph) {
        var unvisitedTransactions = graph.nodes().stream().collect(Collectors.toSet());
        List<Transaction<KeyType, ValueType>> path = new ArrayList<>();
        Set<Transaction<KeyType, ValueType>> pathSet = new HashSet<>();
        while (!unvisitedTransactions.isEmpty()) {
            var txn = unvisitedTransactions.iterator().next();
            Deque<Transaction<KeyType, ValueType>> stack = new ArrayDeque<>();
            stack.push(txn);
            while (!stack.isEmpty()) {
                var t = stack.peek();
                if (!unvisitedTransactions.contains(t)) {
                    // 处理过了它和它的所有邻居
                    stack.pop();
                    path.remove(t);
                    pathSet.remove(t);
                    continue;
                }
                unvisitedTransactions.remove(t);
                path.add(t);
                pathSet.add(t);
                var nextTxnSet = graph.successors(t);
                for (var nextTxn : nextTxnSet) {
                    if (pathSet.contains(nextTxn)) {
                        // 有环
                        System.out.println("CounterExample loop in dep graph:");
                        int start = path.indexOf(nextTxn);
                        for (int i = start; i < path.size(); ++i) {
                            printTransaction(path.get(i));
                            System.out.println("-------------");
                        }
                        continue;
                    }
                    if (!unvisitedTransactions.contains(nextTxn)) {
                        // 处理过了
                        continue;
                    }
                    stack.push(nextTxn);
                }
            }
        }
    }

    static <KeyType, ValueType> boolean verifyInternalConsistency(History<KeyType, ValueType> history) {
        var writes = new HashMap<Pair<KeyType, ValueType>, Pair<Event<KeyType, ValueType>, Integer>>();
        var txnWrites = new HashMap<Pair<Transaction<KeyType, ValueType>, KeyType>, ArrayList<Integer>>();
        var getEvents = ((Function<Event.EventType, Stream<Pair<Integer, Event<KeyType, ValueType>>>>) type -> history
                .getTransactions().stream().flatMap(txn -> {
                    var events = txn.getEvents();
                    return IntStream.range(0, events.size()).mapToObj(i -> Pair.of(i, events.get(i)))
                            .filter(p -> p.getRight().getType() == type);
                }));

        getEvents.apply(Event.EventType.WRITE).forEach(p -> {
            var i = p.getLeft();
            var ev = p.getRight();
            writes.put(Pair.of(ev.getKey(), ev.getValue()), Pair.of(ev, i));
            txnWrites.computeIfAbsent(Pair.of(ev.getTransaction(), ev.getKey()), k -> new ArrayList()).add(i);
        });

        for (var p : getEvents.apply(Event.EventType.READ).collect(Collectors.toList())) {
            var i = p.getLeft();
            var ev = p.getRight();
            var writeEv = writes.get(Pair.of(ev.getKey(), ev.getValue()));

            if (writeEv == null) {
                System.err.printf("%s has no corresponding write\n", ev);
                return false;
            }

            var writeIndices = txnWrites.get(Pair.of(writeEv.getLeft().getTransaction(), writeEv.getLeft().getKey()));
            var j = Collections.binarySearch(writeIndices, writeEv.getRight());

            if (writeEv.getLeft().getTransaction() == ev.getTransaction()) {
                if (j != writeIndices.size() - 1 && writeIndices.get(j + 1) < i) {
                    System.err.printf("%s not reading from latest write: %s\n", ev, writeEv.getLeft());
                    return false;
                } else if (writeEv.getRight() > i) {
                    System.err.printf("%s reads from a write after it: %s\n", ev, writeEv.getLeft());
                    return false;
                }
            } else if (j != writeIndices.size() - 1) {
                System.err.printf("%s not reading from latest write: %s\n", ev, writeEv.getLeft());
                return false;
            }
        }
        return true;
    }

    /**
     * Collect unknown edges
     *
     * @param graphA       graph A containing known and unknown edges
     * @param graphB       graph B containing known and unknown edges
     * @param reachability known reachable node pairs. Edges that connect reachable
     *                     pairs are not collected
     * @param solver       SAT solver
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getUnknownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphA,
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphB,
            MatrixGraph<Transaction<KeyType, ValueType>> reachability, Solver solver) {
        var edges = new ArrayList<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>>();

        for (var p : graphA.nodes()) {
            for (var n : graphA.successors(p)) {
                var predEdges = graphA.edgeValue(p, n).get();

                if (p == n || !reachability.hasEdgeConnecting(p, n)) {
                    predEdges.forEach(e -> edges.add(Triple.of(p, n, e)));
                }

                var txns = graphB.successors(n).stream()
                        .filter(t -> p == t || !reachability.hasEdgeConnecting(p, t))
                        .collect(Collectors.toList());

                for (var s : txns) {
                    var succEdges = graphB.edgeValue(n, s).get();
                    predEdges.forEach(e1 -> succEdges.forEach(e2 -> {
                        var lit = Logic.and(e1, e2);
                        solver.setDecisionLiteral(lit, false);
                        edges.add(Triple.of(p, s, lit));
                    }));
                }
            }
        }

        return edges;
    }

    /**
     * Collect known edges in A union C
     *
     * @param graphA known graph A
     * @param graphB known graph B
     * @param AC     the graph containing the edges to collect
     */
    static <KeyType, ValueType> List<Triple<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>, Lit>> getKnownEdges(
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphA,
            MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> graphB,
            MatrixGraph<Transaction<KeyType, ValueType>> AC) {
        return AC.edges().stream().map(e -> {
            var n = e.source();
            var m = e.target();
            var firstEdge = ((Function<Optional<Collection<Lit>>, Lit>) c -> c.get().iterator().next());

            if (graphA.hasEdgeConnecting(n, m)) {
                return Triple.of(n, m, firstEdge.apply(graphA.edgeValue(n, m)));
            }

            var middle = Sets.intersection(graphA.successors(n), graphB.predecessors(m)).iterator().next();
            return Triple.of(n, m, Logic.and(firstEdge.apply(graphA.edgeValue(n, middle)),
                    firstEdge.apply(graphB.edgeValue(middle, m))));
        }).collect(Collectors.toList());
    }

    static <KeyType, ValueType> Map<Transaction<KeyType, ValueType>, Integer> getOrderInSession(
            History<KeyType, ValueType> history) {
        // @formatter:off
        return history.getSessions().stream()
                .flatMap(s -> Streams.zip(
                    s.getTransactions().stream(),
                    IntStream.range(0, s.getTransactions().size()).boxed(),
                    Pair::of))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));
        // @formatter:on
    }

    static <KeyType, ValueType> MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> createEmptyGraph(
            History<KeyType, ValueType> history) {
        MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g = ValueGraphBuilder.directed()
                .allowsSelfLoops(true).build();

        history.getTransactions().forEach(g::addNode);
        return g;
    }

    static <KeyType, ValueType> void addEdge(MutableValueGraph<Transaction<KeyType, ValueType>, Collection<Lit>> g,
            Transaction<KeyType, ValueType> src, Transaction<KeyType, ValueType> dst, Lit lit) {
        if (!g.hasEdgeConnecting(src, dst)) {
            g.putEdgeValue(src, dst, new ArrayList<>());
        }
        g.edgeValue(src, dst).get().add(lit);
    }

    /*
     * Delete edges in a way that preserves reachability
     */
    static <KeyType, ValueType> MatrixGraph<Transaction<KeyType, ValueType>> reduceEdges(
            MatrixGraph<Transaction<KeyType, ValueType>> graph,
            Map<Transaction<KeyType, ValueType>, Integer> orderInSession) {
        System.err.printf("Before: %d edges\n", graph.edges().size());
        var newGraph = MatrixGraph.ofNodes(graph);

        for (var n : graph.nodes()) {
            var succ = graph.successors(n);
            // @formatter:off
            var firstInSession = succ.stream()
                .collect(Collectors.toMap(
                    m -> m.getSession(),
                    Function.identity(),
                    (p, q) -> orderInSession.get(p)
                        < orderInSession.get(q) ? p : q));

            firstInSession.values().forEach(m -> newGraph.putEdge(n, m));

            succ.stream()
                .filter(m -> m.getSession() == n.getSession()
                        && orderInSession.get(m) == orderInSession.get(n) + 1)
                .forEach(m -> newGraph.putEdge(n, m));
            // @formatter:on
        }

        System.err.printf("After: %d edges\n", newGraph.edges().size());
        return newGraph;
    }

    static <KeyType, ValueType> String conflictsToDot(Collection<Transaction<KeyType, ValueType>> transactions,
            Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> edges,
            Collection<SIConstraint<KeyType, ValueType>> constraints) {
        var builder = new StringBuilder();
        builder.append("digraph {\n");

        for (var txn : transactions) {
            builder.append(String.format("\"%s\";\n", txn));
        }

        for (var e : edges) {
            var pair = e.getLeft();
            var keys = e.getRight();
            var label = new StringBuilder();

            for (var k : keys) {
                if (k.getType() != EdgeType.SO) {
                    label.append(String.format("%s %s\\n", k.getType(), k.getKey()));
                } else {
                    label.append(String.format("%s\\n", k.getType()));
                }
            }

            builder.append(
                    String.format("\"%s\" -> \"%s\" [label=\"%s\"];\n", pair.source(), pair.target(), label));
        }

        int colorStep = 0x1000000 / (constraints.size() + 1);
        int color = 0;
        for (var c : constraints) {
            color += colorStep;
            for (var e : c.getEdges1()) {
                builder.append(String.format("\"%s\" -> \"%s\" [style=dotted,color=\"#%06x\"];\n", e.getFrom(), e.getTo(), color));
            }

            for (var e : c.getEdges2()) {
                builder.append(String.format("\"%s\" -> \"%s\" [style=dashed,color=\"#%06x\"];\n", e.getFrom(), e.getTo(), color));
            }
        }

        builder.append("}\n");
        return builder.toString();
    }

    static <KeyType, ValueType> String conflictsToLegacy(Collection<Transaction<KeyType, ValueType>> transactions,
            Collection<Pair<EndpointPair<Transaction<KeyType, ValueType>>, Collection<Edge<KeyType>>>> edges,
            Collection<SIConstraint<KeyType, ValueType>> constraints) {
        var builder = new StringBuilder();

        edges.forEach(p -> builder.append(String.format("Edge: %s\n", p)));
        constraints.forEach(c -> builder.append(String.format("Constraint: %s\n", c)));
        builder.append(String.format("Related transactions:\n"));
        transactions.forEach(t -> {
            builder.append(String.format("sessionid: %d, id: %d\nops:\n", t.getSession().getId(), t.getId()));
            t.getEvents()
                    .forEach(e -> builder.append(String.format("%s %s = %s\n", e.getType(), e.getKey(), e.getValue())));
        });

        return builder.toString();
    }
}
