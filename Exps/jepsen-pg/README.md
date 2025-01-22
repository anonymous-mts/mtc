# jepsen.pg



PostgreSQL Testing

## Usage



#### 1、Enter the jepsen directory and build jepsen.

```
cd /Exps/jepsen-pg/jepsen
lein install
```

#### 2、Enter the stolon folder and run the command to test PostgreSQL.

```
cd /Exps/jepsen-pg/stolon
```

```
lein run test -w mini --test-count 10  --max-txn-length 10 --txn-num 1000 --max-writes-per-key 100000 --concurrency 50 -r 500 --isolation serializable --time-limit 240 --nemesis none --existing-postgres --node localhost --no-ssh --postgres-user postgres --postgres-password postgres
```

```
lein run test-all -w append --test-count 10  --max-txn-length 10 --txn-num 1000 --max-writes-per-key 16 --concurrency 50 -r 200 --isolation serializable --time-limit 120 --nemesis none --existing-postgres --node localhost --no-ssh --postgres-user postgres --postgres-password postgres
```



## Test



If the above code runs successfully, you can use the existing script to test PostgreSQL under different parameters.

#### 1、Test append

##### 1.1  ``run.py``

Modify ``workload`` in the file to append.

Modify ``max_txn_length_set`` in the file to test multiple sets of ``max_txn_length`` (maximum transaction length).

##### 1.2  ``batchrun.py``（This will call ``run.py``)

Modify ``txn_number`` in the file to test multiple sets of ``txn_number`` (number of transactions to test).

Modify ``max_writes_per_key`` in the file to test multiple sets of ``max_writes_per_key`` (maximum writes per key).

Modify ``test_cnt`` in the file to determine the number of tests for each parameter set.

Use ``python3 batchrun.py`` to start the test.



#### 2、Test mini

##### 2.1  ``run_mini.py``

Modify ``workload`` in the file to mini.

Modify ``max_txn_length_set`` in the file to test multiple sets of ``max_txn_length`` (maximum transaction length).

##### 2.2  ``batchrun_mini.py``（This will call ``run_mini.py``)

Modify ``txn_number`` in the file to test multiple sets of ``txn_number`` (number of transactions to test).

Modify ``max_writes_per_key`` in the file to test multiple sets of ``max_writes_per_key`` (maximum writes per key).

Modify ``test_cnt`` in the file to determine the number of tests for each parameter set.

Use ``python3 batchrun_mini.py`` to start the test.



## Options


Refer to ``lein run test --help`` to learn about all the options.

 ``--txn_number NUM `` specifies the number of transactions for this test.

`` --test-count NUM `` specifies the number of times to run this test.

 ``--max-txn-length NUM `` (maximum transaction length) and `` max-writes-per-key `` (maximum writes per key) specify the maximum size of a transaction and the number of times to write to any single key before selecting a new key.

``--nemesis FAULTS`` is a comma-separated list of faults, while ``--nemesis-interval SECONDS`` controls the approximate interval time between nemesis operations for each fault type.

``--rate -r NUM`` controls the maximum number of operations Jepsen attempts to execute per second.

``-v VERSION ``controls the version of PostgreSQL that we install and test.

``-w WORKLOAD`` tells Jepsen which workload to run, such as ``append``，``mini``。

``--concurrency NUM`` tells Jepsen the level of concurrency to run.

``--isolation`` specifies the isolation level to be tested by Jepsen.

``--time-limit NUM`` controls the runtime duration of the Jepsen test.

``--postgres-user  `` specifies the PostgreSQL database username.

``--postgres-password``specifies the PostgreSQL database password.



## License


Copyright © 2020 Jepsen, LLC

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License, v. 2.0 are satisfied: GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version, with the GNU Classpath Exception which is available at https://www.gnu.org/software/classpath/license.html.
