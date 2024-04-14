package bench.generalBench;

import org.apache.commons.lang3.text.translate.NumericEntityUnescaper.OPTION;

import bench.BenchUtils;
import bench.Transaction;
import bench.generalBench.GeneralTxnConstants.OP_TYPE;
import kv_interfaces.KvInterface;
import kvstore.exceptions.KvException;
import kvstore.exceptions.TxnException;
import main.Profiler;

public class GeneralTransaction extends Transaction {

    private OP_TYPE[] ops;
    private String[] keys;

    public GeneralTransaction(KvInterface kvi, OP_TYPE[] ops, String[] keys) {
		super(kvi, "general");
		this.ops = ops;
        this.keys = keys;
	}

	@Override
    public void inputGeneration() {
        // do nothing
    }

    @Override
    public boolean doTansaction() throws KvException, TxnException {
        Profiler.getInstance().startTick("kvi");
        beginTxn();
        if (this.ops == null) {
            // initial insert
            for (String key : this.keys) {
                kvi.insert(txn, key, BenchUtils.getRandomValue());
            }
        } else {
            for (int i = 0; i < ops.length; ++i) {
                OP_TYPE op = ops[i];
                String key = keys[i];
                if (op == OP_TYPE.READ) {
                    kvi.get(txn, key);
                } else {
                    kvi.set(txn, key, BenchUtils.getRandomValue());
                }
            }
        }
        commitTxn();
        Profiler.getInstance().endTick("kvi");
        return true;
    }
}
