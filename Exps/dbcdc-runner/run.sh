#!/bin/bash
lein run test-all -w rw \
--key-count 100 \
--max-writes-per-key 4 \
--concurrency 50 \
--txn-num 3000 \
-r 500 \
--time-limit 1440000 \
--test-count 1 \
--max-txn-length 8 \
--nemesis none \
--existing-postgres \
--node dummy-node \
--isolation serializable \
--expected-consistency-model serializable \
--no-ssh \
--database postgresql \

# --varchar-table

# lein run test-all -w rw \
# --key-count 100 \
# --max-writes-per-key 100 \
# --concurrency 10 \
# --txn-num 1000 \
# -r 500 \
# --time-limit 1440000 \
# --nemesis none \
# --existing-postgres \
# --node dummy-node \
# --isolation serializable \
# --expected-consistency-model serializable \
# --no-ssh \
# --database postgresql \