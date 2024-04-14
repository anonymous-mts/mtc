package verifier;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import org.jgrapht.alg.util.Pair;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;

import history.Event;
import history.Event.EventType;
import util.Profiler;
import history.History;
import history.HistoryLoader;
import history.Transaction;

public class MiniVerifier<KeyType, ValueType> {
    private final History<KeyType, ValueType> history;

    private final Boolean log;

    public MiniVerifier(HistoryLoader<KeyType, ValueType> loader, Boolean log) {
        this.log = log;
        history = loader.loadHistory();
        System.err.printf("Sessions count: %d\nTransactions count: %d\nEvents count: %d\n",
                history.getSessions().size(), history.getTransactions().size(), history.getEvents().size());
    }

    private void printTransaction(Transaction<KeyType, ValueType> txn) {
        System.out.println(txn);
        for (var event : txn.getEvents()) {
            System.out.println("\t" + event);
        }
    }

    public boolean audit() {
        var profiler = Profiler.getInstance();
        MutableGraph<Transaction<KeyType, ValueType>> graph = GraphBuilder.directed().build();
        Collection<Transaction<KeyType, ValueType>> transactions = history.getTransactions();
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
        }

        Map<Pair<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> read2Txns = new HashMap<>();
        Map<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>> write2Txn = new HashMap<>();
        extractReadAndWriteMappingForHistory(read2Txns, write2Txn);

        // 统计WR边，WW边和RW边，添加WR边和WW边
        profiler.startTick("WR, WW, RW");
        Map<Transaction<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> WREdges = new HashMap<>();
        Map<Transaction<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> WWEdges = new HashMap<>();
        Map<Transaction<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> RWEdges = new HashMap<>();
        Map<Pair<Transaction<KeyType, ValueType>, Transaction<KeyType, ValueType>>, Transaction<KeyType, ValueType>> RWEdge2Mid = new HashMap<>();
        var writeEntrys = write2Txn.entrySet();
        for (var writeEntry : writeEntrys) {
            var writeKV = writeEntry.getKey();
            var writeTxn = writeEntry.getValue();
            var readTxns = read2Txns.getOrDefault(writeKV, new HashSet<>());
            Transaction<KeyType, ValueType> nextWriteTxn = null;
            for (var readTxn : readTxns) {
                if (writeTxn == readTxn) {
                    // 自读，类似于R1W1R1
                    continue;
                }

                // 统计WR边
                var txns = WREdges.getOrDefault(writeTxn, new HashSet<>());
                txns.add(readTxn);
                WREdges.put(writeTxn, txns);
                if (log) {
                    System.out.println("Add WR Edge for" + writeTxn + ", " + readTxn + " for key " + writeKV);
                }

                // 由于WR边和WW边在Mini+Unique的条件下存在WW被包含在WR里面，所以只在WR下加入graph即可
                graph.putEdge(writeTxn, readTxn);

                // 统计WW边
                for (var event : readTxn.getEvents()) {
                    if (event.getType() == EventType.WRITE && event.getKey().equals(writeKV.getFirst())) {
                        // 如果这个txn不但读了同样的key，而且写了，那么就统计到了WW边
                        if (log) {
                            System.out.println("Add WW Edge for" + writeTxn + ", " + readTxn + " for key " + writeKV);
                        }
                        if (nextWriteTxn != null) {
                            if (nextWriteTxn == readTxn) {
                                // 同一个事务写了同一个key同一个值两次，如R1R1W1W1，跳过
                                continue;
                            }
                            // 如果已经有了一个针对该key的WW边，那么从一个txn针对同一个key延伸出来两条WW边，直接返回false
                            if (log) {
                                System.out.println("duplicate WW:");
                                System.out.println(nextWriteTxn.toString());
                                System.out.println(readTxn.toString());
                            }
                            return false;
                        }
                        nextWriteTxn = readTxn;
                        var txns2 = WWEdges.getOrDefault(writeTxn, new HashSet<>());
                        txns2.add(readTxn);
                        WWEdges.put(writeTxn, txns2);
                    }
                }
            }
            // 统计RW边
            for (var readTxn : readTxns) {
                if (readTxn != nextWriteTxn && nextWriteTxn != null) {
                    var txns = RWEdges.getOrDefault(readTxn, new HashSet<>());
                    txns.add(nextWriteTxn);
                    RWEdges.put(readTxn, txns);
                    RWEdge2Mid.put(new Pair<>(readTxn, nextWriteTxn), writeTxn);
                }
            }
            read2Txns.remove(writeKV);
        }
        if (!read2Txns.isEmpty()) {
            // 存在txn，读到的key从来没有被写过
            if (log) {
                System.out.println("orphan read:");
                for (var read : read2Txns.keySet()) {
                    System.out.println(read.toString());
                }
            }
            return false;
        }
        if (log) {
            System.out.println("WREdges:");
            for (var entry : WREdges.entrySet()) {
                System.out.println(entry.getKey());
                for (var e : entry.getValue()) {
                    System.out.println("\t" + e);
                }
            }

            System.out.println("WWEdges:");
            for (var entry : WWEdges.entrySet()) {
                System.out.println(entry.getKey());
                System.out.println("\t" + entry.getValue());
            }
            System.out.println("RW Edges:");
            for (var entry : RWEdges.entrySet()) {
                System.out.println(entry.getKey());
                for (var e : entry.getValue()) {
                    System.out.println("\t" + e);
                }
            }
        }
        profiler.endTick("WR, WW, RW");

        // 添加SO边
        profiler.startTick("SO");
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
        profiler.endTick("SO");

        // 添加SO/WW/WR + RW的组合边
        profiler.startTick("SO/WW/WR + RW");
        var SOEntries = SOEdges.entrySet();
        for (var SOEntry : SOEntries) {
            var source = SOEntry.getKey();
            var mid = SOEntry.getValue();
            var targets = RWEdges.get(mid);
            if (targets != null) {
                for (var target : targets) {
                    if (source != target) {
                        graph.putEdge(source, target);
                    } else {
                        if (log) {
                            printTransaction(source);
                            printTransaction(mid);
                            printTransaction(RWEdge2Mid.get(new Pair<>(mid, target)));
                            printTransaction(target);
                            System.out.println("SO+RW has self cycle");
                        }
                        return false;
                    }
                }
            }
        }
        var WWEntries = WWEdges.entrySet();
        for (var WWEntry : WWEntries) {
            var source = WWEntry.getKey();
            var mids = WWEntry.getValue();
            for (var mid : mids) {
                var targets = RWEdges.get(mid);
                if (targets != null) {
                    for (var target : targets) {
                        if (source != target) {
                            graph.putEdge(source, target);
                        } else {
                            if (log) {
                                System.out.println("WW+RW has self cycle");
                                printTransaction(source);
                                printTransaction(mid);
                            }
                            return false;
                        }
                    }
                }
            }
        }
        var WREntries = WREdges.entrySet();
        for (var WREntry : WREntries) {
            var source = WREntry.getKey();
            var mids = WREntry.getValue();
            for (var mid : mids) {
                var targets = RWEdges.get(mid);
                if (targets != null) {
                    for (var target : targets) {
                        if (source != target) {
                            graph.putEdge(source, target);
                        } else {
                            if (log) {
                                System.out.println("WR+RW has self cycle");
                                printTransaction(source);
                                printTransaction(mid);
                            }
                            return false;
                        }
                    }
                }
            }
        }
        profiler.endTick("SO/WW/WR + RW");

        profiler.startTick("cycle detection");
        var cyclic = !Graphs.hasCycle(graph);
        profiler.endTick("cycle detection");

        if (log && cyclic) {
            // 打印counterexample
            Utils.printCounterExample(graph);
        }
        return cyclic;
    }

    private void extractReadAndWriteMappingForHistory(
            Map<Pair<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> read2Txns,
            Map<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>> write2Txn) {
        Collection<Transaction<KeyType, ValueType>> transactions = history.getTransactions();
        for (Transaction<KeyType, ValueType> transaction : transactions) {
            extractReadAndWriteMappingForTransaction(transaction, read2Txns, write2Txn);
        }
    }

    private void extractReadAndWriteMappingForTransaction(
            Transaction<KeyType, ValueType> transaction,
            Map<Pair<KeyType, ValueType>, Set<Transaction<KeyType, ValueType>>> read2Txns,
            Map<Pair<KeyType, ValueType>, Transaction<KeyType, ValueType>> write2Txn) {
        List<Event<KeyType, ValueType>> events = transaction.getEvents();
        Event<KeyType, ValueType> lastRead = null;
        Event<KeyType, ValueType> lastWrite = null;
        for (Event<KeyType, ValueType> event : events) {
            if (event.getType() == EventType.READ) {
                if (lastRead != null && lastRead.getKey().equals(event.getKey())) {
                    // 处理一个事务读了两次同一个key，相当于只有第一个读生效
                    continue;
                }
                lastRead = event;
                var txns = read2Txns.getOrDefault(new Pair<>(event.getKey(), event.getValue()), new HashSet<>());
                txns.add(transaction);
                read2Txns.put(new Pair<>(event.getKey(), event.getValue()), txns);
            } else if (event.getType() == EventType.WRITE) {
                if (lastWrite != null && lastWrite.getKey().equals(event.getKey())) {
                    // 处理一个事务写了两次同一个key，相当于只有后一个写生效
                    write2Txn.remove(new Pair<>(lastWrite.getKey(), lastWrite.getValue()));
                }
                if (write2Txn.get(new Pair<>(event.getKey(), event.getValue())) != null) {
                    assert false;
                } else {
                    write2Txn.put(new Pair<>(event.getKey(), event.getValue()), transaction);
                }
            }
        }
    }
}
