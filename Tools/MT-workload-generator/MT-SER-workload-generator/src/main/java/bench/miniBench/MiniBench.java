package bench.miniBench;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.text.translate.NumericEntityUnescaper.OPTION;

import bench.BenchUtils;
import bench.Benchmark;
import bench.Transaction;
import bench.miniBench.MiniTxnConstants.OP_TYPE;
import bench.miniBench.MiniTxnConstants.TASK_TYPE;
import kv_interfaces.KvInterface;
import main.Config;

public class MiniBench extends Benchmark {

    private static AtomicInteger keyNum;
    private Random rand = new Random(Config.get().SEED);
    private List<Integer> keyIndexs = null;
    private Iterator<Integer> keyIndexIterator = null;

    public MiniBench(KvInterface kvi) {
        super(kvi);
        keyNum = new AtomicInteger(Config.get().KEY_INDX_START);
        keyIndexs = BenchUtils.getKeySeq(Config.get().NUM_KEYS, Config.get().TXN_NUM * 4, Config.get().KEY_DIST);
        keyIndexIterator = keyIndexs.iterator();
        if (Config.get().SKIP_LOADING) {
            keyNum.addAndGet(Config.get().NUM_KEYS);
        }
    }

    @Override
    public Transaction[] preBenchmark() {
        // TODO need to clear the database

        // Fill in some keys
        int num_txn = Config.get().NUM_KEYS;
        Transaction ret[] = new Transaction[num_txn];
        for (int i = 0; i < num_txn; i++) {
            ret[i] = getTheTxn(TASK_TYPE.INSERT_INIT);
        }
        return ret;
    }

    private String getExistingKey() {
        int cur_indx = keyIndexIterator.next();
        return Config.get().KEY_PRFIX + cur_indx;
    }

    private String[] getExistingKeys(int num) {
        HashSet<String> visited = new HashSet<>();
        String k = null;
        for (int i = 0; i < num; i++) {
            do {
                k = getExistingKey();
            } while (visited.contains(k));
            visited.add(k);
        }
        return visited.toArray(new String[0]);
    }

    private String getNewKey() {
        int cur_indx = keyNum.getAndIncrement();
        return Config.get().KEY_PRFIX + cur_indx;
    }

    private Transaction getTheTxn(TASK_TYPE type) {
        if (type == TASK_TYPE.INSERT_INIT) {
            String[] keys = new String[] { getNewKey() };
            return new MiniTransaction(kvi, type, keys);
        }
        OP_TYPE[] ops = type.getOps();
        int numKey = 1;
        for (OP_TYPE op : ops) {
            if (op.equals(OP_TYPE.READ_2) || op.equals(OP_TYPE.WRITE_2)) {
                numKey = 2;
            }
        }
        String[] keys = getExistingKeys(numKey);
        return new MiniTransaction(kvi, type, keys);
    }

    @Override
    public Transaction getNextTxn() {
        // randomly generate a TASK_TYPE
        TASK_TYPE[] types = TASK_TYPE.values();
        int randomIndex = rand.nextInt(types.length);
        while (types[randomIndex] == TASK_TYPE.INSERT_INIT) {
            randomIndex = rand.nextInt(types.length);
        }
        TASK_TYPE type = types[randomIndex];

        return getTheTxn(type);
    }

    @Override
    public void afterBenchmark() {
        // do nothing
    }

    @Override
    public String[] getTags() {
        TASK_TYPE[] types = TASK_TYPE.values();
        String[] tags = new String[types.length];
        for (int i = 0; i < types.length; ++i) {
            tags[i] = types[i].toString();
        }
        return tags;
    }
}
