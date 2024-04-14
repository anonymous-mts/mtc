package bench.miniBench;

import main.Logger;

public class MiniTxnConstants {
    enum TASK_TYPE {
        INSERT_INIT,
        R1,
        R1R2,
        R1R1,
        R1W1,
        R1R2W2,
        R2R1W2,
        R1R1W1,
        R2W2R1,
        R1W1R1,
        R1R2W1W2,
        R1R2W2W1,
        R1W1R2W2,
        R1R1W1W1,
        R1W1R1W1;

        public static TASK_TYPE[] keyDistFirstTypes() {
            return new TASK_TYPE[] {
                    R1,
                    R1R2,
                    R1W1,
                    R1R2W2,
                    R2R1W2,
                    R2W2R1,
                    R1R2W1W2,
                    R1R2W2W1,
                    R1W1R2W2,
            };
        }

        public OP_TYPE[] getOps() {
            switch (this) {
                case INSERT_INIT:
                    return new OP_TYPE[] { OP_TYPE.INSERT };
                case R1:
                    return new OP_TYPE[] { OP_TYPE.READ_1 };
                case R1R2:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_2 };
                case R1R1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_1 };
                case R1W1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.WRITE_1 };
                case R1R2W2:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_2, OP_TYPE.WRITE_2 };
                case R2R1W2:
                    return new OP_TYPE[] { OP_TYPE.READ_2, OP_TYPE.READ_1, OP_TYPE.WRITE_2 };
                case R1R1W1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_1, OP_TYPE.WRITE_1 };
                case R2W2R1:
                    return new OP_TYPE[] { OP_TYPE.READ_2, OP_TYPE.WRITE_2, OP_TYPE.READ_1 };
                case R1W1R1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.WRITE_1, OP_TYPE.READ_1 };
                case R1R2W1W2:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_2, OP_TYPE.WRITE_1, OP_TYPE.WRITE_2 };
                case R1R2W2W1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_2, OP_TYPE.WRITE_2, OP_TYPE.WRITE_1 };
                case R1W1R2W2:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.WRITE_1, OP_TYPE.READ_2, OP_TYPE.WRITE_2 };
                case R1R1W1W1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.READ_1, OP_TYPE.WRITE_1, OP_TYPE.WRITE_1 };
                case R1W1R1W1:
                    return new OP_TYPE[] { OP_TYPE.READ_1, OP_TYPE.WRITE_1, OP_TYPE.READ_1, OP_TYPE.WRITE_1 };
            }
            return new OP_TYPE[] {};
        }

        public String getOpTag() {
            switch (this) {
                case INSERT_INIT:
                    return "INSERT_INIT";
                case R1:
                    return "R1";
                case R1R2:
                    return "R1R2";
                case R1R1:
                    return "R1R1";
                case R1W1:
                    return "R1W1";
                case R1R2W2:
                    return "R1R2W2";
                case R2R1W2:
                    return "R2R1W2";
                case R1R1W1:
                    return "R1R1W1";
                case R2W2R1:
                    return "R2W2R1";
                case R1W1R1:
                    return "R1W1R1";
                case R1R2W1W2:
                    return "R1R2W1W2";
                case R1R2W2W1:
                    return "R1R2W2W1";
                case R1W1R2W2:
                    return "R1W1R2W2";
                case R1R1W1W1:
                    return "R1R1W1W1";
                case R1W1R1W1:
                    return "R1W1R1W1";
            }
            Logger.logError("UNKOWN TASK_TYPE[" + this + "]");
            return "Wrong Txn";
        }
    }

    enum OP_TYPE {
        INSERT,
        READ_1,
        WRITE_1,
        READ_2,
        WRITE_2;
    }

}
