# DB-CDC-PostgreSQL



PostgreSQL Testing

## Usage



#### 1、Enter dbcdcd-runner and configure the data source.

Modify`resources/db-config.edn`，the configuration file, and add the `:postgresql` key:

```clojure
:postgresql {:dbtype    "postgresql"
              :host      "localhost"
              :port      5432
              :user      "postgres"
              :password  "postgres"
              :dbname    "test"
              :sslmode   "disable"}
```

#### 2、Enter the dbcdcd-runner folder and run the command to test PostgreSQL.

```
cd dbcdc-runner
```

```
lein run test --key-count 100 --max-writes-per-key 10 --concurrency 50 --rate 500 --time-limit 1440000 --max-txn-length 10 --txn-num 1000 --test-count 10 --isolation serializable --nemesis none --existing-postgres --node localhost --no-ssh --database postgresql
```



## Test



If the code above runs successfully, you can use the existing script to test PostgreSQL under different parameters.

#### 1、Test RW

##### 1.1  ``run.py``

Modify ``workload`` to ``rw`` in the file.

Modify ``max_txn_length_set`` in the file to test multiple sets of ``max_txn_length`` (maximum transaction length).

##### 1.2  ``batchrun.py``（This will call ``run.py``)

Modify ``txn_number`` in the file to test multiple sets of ``txn_number`` (the number of transactions to test).

Modify ``max_writes_per_key`` in the file to test multiple sets of ``max_writes_per_key`` (the maximum write count per key).

Modify ``test_cnt`` in the file to determine the number of tests for each parameter set.

Use ``python3 batchrun.py`` to start the test.


## Options



Refer to ``lein run test --help`` to learn about all the options.

``--key-count NUM `` specifies the number of keys for this test.

 ``--txn_number NUM ``  specifies the number of transactions for this test.

`` --test-count NUM `` specifies the number of times to run this test.

 ``--max-txn-length NUM `` （maximum transaction length）和 `` max-writes-per-key `` （maximum writes per key）specify the maximum size of a transaction and the number of times to write to any single key before selecting a new key.

``--nemesis FAULTS``is a comma-separated list of faults, while ``--nemesis-interval SECONDS`` controls the approximate interval time between nemesis operations for each fault type.

``--rate -r NUM`` controls the maximum number of operations Jepsen attempts to execute per second.

``-v VERSION ``controls the version of PostgreSQL that we install and test.

``--concurrency NUM`` tells Jepsen the level of concurrency to run.

``--isolation``  specifies the isolation level to be tested by Jepsen.

``--time-limit NUM``controls the runtime duration of the Jepsen test.

``--database   `` specifies the database to be tested by Jepsen.

