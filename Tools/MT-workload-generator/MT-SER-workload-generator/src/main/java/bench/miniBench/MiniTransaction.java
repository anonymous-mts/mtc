package bench.miniBench;

import bench.BenchUtils;
import bench.Transaction;
import bench.miniBench.MiniTxnConstants.OP_TYPE;
import bench.miniBench.MiniTxnConstants.TASK_TYPE;
import kv_interfaces.KvInterface;
import kvstore.exceptions.KvException;
import kvstore.exceptions.TxnException;
import main.Profiler;

public class MiniTransaction extends Transaction {
    private TASK_TYPE taskType;
    private String[] keys;

    public MiniTransaction(KvInterface kvi, TASK_TYPE taskType, String[] keys) {
        super(kvi, taskType.getOpTag());
        this.taskType = taskType;
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
        Profiler.getInstance().endTick("kvi");
        OP_TYPE[] ops = taskType.getOps();
        String v1 = "", v2 = "";
        Profiler.getInstance().startTick("kvi");
        for (OP_TYPE op : ops) {
            switch (op) {
                case INSERT:
                    kvi.insert(txn, this.keys[0], BenchUtils.getRandomValue());
                    break;
                case READ_1:
                    v1 = kvi.get(txn, this.keys[0]);
                    break;
                case READ_2:
                    v2 = kvi.get(txn, this.keys[1]);
                    break;
                case WRITE_1:
                    kvi.set(txn, keys[0], v1 + "M");
                    break;
                case WRITE_2:
                    kvi.set(txn, keys[1], v2 + "M");
                    break;
                default:
                    assert false;
                    break;
            }
        }
        commitTxn();
        Profiler.getInstance().endTick("kvi");
        return true;
    }

}
