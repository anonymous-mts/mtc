package verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jgrapht.alg.util.Pair;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import history.Event;
import history.History;
import history.HistoryLoader;
import history.Transaction;
import history.Event.EventType;
import util.Profiler;

public class SERVerifier<KeyType, ValueType> {
    private final History<KeyType, ValueType> history;

    public SERVerifier(HistoryLoader<KeyType, ValueType> loader) {
        history = loader.loadHistory();
        System.err.printf("Sessions count: %d\nTransactions count: %d\nEvents count: %d\n",
                history.getSessions().size(), history.getTransactions().size(), history.getEvents().size());
    }

    public boolean audit() {
        MutableGraph<Transaction<KeyType, ValueType>> graph = GraphBuilder.directed().build();
        Collection<Transaction<KeyType, ValueType>> transactions = history.getTransactions();
        Map<Pair<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> read2Txns = new HashMap<>();
        Map<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>> write2Txn = new HashMap<>();
        for (Transaction<KeyType, ValueType> transaction : transactions) {
            if (!Utils.verifyInternalConsistency(transaction)) {
                // 内部一致性出问题
                System.out.println("internal consistency violation:");
                Utils.printTransaction(transaction);
                return false;
            }
        }
        for (Transaction<KeyType, ValueType> transaction : transactions) {
            graph.addNode(transaction);

            List<Event<KeyType, ValueType>> events = transaction.getEvents();
            for (Event<KeyType, ValueType> event : events) {
                if (event.getType() == EventType.READ) {
                    var txns = read2Txns.getOrDefault(new Pair<>(event.getKey(), event.getValue()), new HashSet<>());
                    txns.add(transaction);
                    read2Txns.put(new Pair<>(event.getKey(), event.getValue()), txns);
                } else if (event.getType() == EventType.WRITE) {
                    if (write2Txn.get(new Pair<>(event.getKey(), event.getValue())) != null) {
                        assert false;
                    } else {
                        write2Txn.put(new Pair<>(event.getKey(), event.getValue()), transaction);
                    }
                }
            }
        }

        // 添加WR边和WW边
        var writeEntrys = write2Txn.entrySet();
        for (var writeEntry : writeEntrys) {
            var writeKey = writeEntry.getKey();
            var writeTxn = writeEntry.getValue();
            var readTxns = read2Txns.getOrDefault(writeKey, new HashSet<>());
            Transaction<KeyType, ValueType> nextWriteTxn = null;
            for (var readTxn : readTxns) {
                // 添加WR边
                // 由于WR边和WW边在Mini+Unique的条件下存在WW被包含在WR里面，所以只在WR下加入graph即可
                if (writeTxn.equals(readTxn)) {
                    continue;
                }
                graph.putEdge(writeTxn, readTxn);

                // 添加WW边
                for (var event : readTxn.getEvents()) {
                    if (event.getType() == EventType.WRITE && event.getKey().equals(writeKey.getFirst())) {
                        // 如果这个txn不但读了同样的key，而且写了，那么就添加WW边
                        if (nextWriteTxn != null) {
                            return false;
                        }
                        nextWriteTxn = readTxn;
                    }
                }
            }
            // 添加RW边
            for (var readTxn : readTxns) {
                if (nextWriteTxn == null) {
                    break;
                }
                if (readTxn == nextWriteTxn) {
                    continue;
                }
                graph.putEdge(readTxn, nextWriteTxn);
            }
            read2Txns.remove(writeKey);
        }
        if (!read2Txns.isEmpty()) {
            // 存在txn，读到的key从来没有被写过
            return false;
        }

        // 添加SO边
        var sessions = history.getSessions();
        Map<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>> SOEdges = new HashMap<>();
        for (var session : sessions) {
            Transaction<KeyType, ValueType> prev = null;
            var transactionsInSession = session.getTransactions();
            for (var transaction : transactionsInSession) {
                if (prev != null) {
                    SOEdges.put(prev, transaction);
                    graph.putEdge(prev, transaction);
                }
                prev = transaction;
            }
        }

        var profiler = Profiler.getInstance();
        profiler.startTick("CYCLE DETECTION");
        var result = Graphs.hasCycle(graph);
        profiler.endTick("CYCLE DETECTION");

        if (result) {
            Utils.printCounterExample(graph);
        }
        return !result;
    }
}
