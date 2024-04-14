package bench.generalBench;

import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.text.translate.NumericEntityUnescaper.OPTION;

import bench.BenchUtils;
import bench.Benchmark;
import bench.Transaction;
import bench.generalBench.GeneralTxnConstants.OP_TYPE;
import kv_interfaces.KvInterface;
import main.Config;

public class GeneralBench extends Benchmark {
    private static AtomicInteger keyNum;
    private Random rand = new Random(Config.get().SEED);

    public GeneralBench(KvInterface kvi) {
        super(kvi);
        keyNum = new AtomicInteger(Config.get().KEY_INDX_START);
        if (Config.get().SKIP_LOADING) {
            keyNum.addAndGet(Config.get().NUM_KEYS);
        }
    }

    private String getExistingKey() {
        int cur_indx = keyNum.get();
        return Config.get().KEY_PRFIX + BenchUtils.getRandomInt(Config.get().KEY_INDX_START, cur_indx);
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

    private OP_TYPE[] getops(int num) {
        OP_TYPE[] results = new OP_TYPE[num];
        for (int i = 0; i < results.length; ++i) {
            if (rand.nextInt() % 2 == 0) {
                results[i] = OP_TYPE.READ;
            } else {
                results[i] = OP_TYPE.WRITE;
            }
        }
        return results;
    }

    @Override
    public Transaction[] preBenchmark() {
        // Fill in some keys
        int num_txn = Config.get().NUM_KEYS;
        Transaction ret[] = new Transaction[num_txn];
        for (int i = 0; i < num_txn; i++) {
            ret[i] = new GeneralTransaction(kvi, null, new String[] { getNewKey() });
        }
        return ret;
    }

    @Override
    public Transaction getNextTxn() {
        // TODO Auto-generated method stub
        return new GeneralTransaction(kvi, getops(Config.get().OP_PER_GENERALTXN), getExistingKeys(Config.get().OP_PER_GENERALTXN));
    }

    @Override
    public void afterBenchmark() {
        // do nothing
    }

    @Override
    public String[] getTags() {
        return new String[] { "general" };
    }
}
