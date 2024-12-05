# jepsen.pg



PostgreSQL 测试

## Usage



#### 1、进入jepsen目录下，构建jepsen

```
cd mini-testing-artifact/Exps/jepsen-pg/jepsen
lein install
```

#### 2、进入stolon文件夹，运行命令测试pg

```
cd mini-testing-artifact/Exps/jepsen-pg/stolon
```

```
lein run test -w mini --test-count 10  --max-txn-length 10 --txn-num 1000 --max-writes-per-key 100000 --concurrency 50 -r 500 --isolation serializable --time-limit 240 --nemesis none --existing-postgres --node localhost --no-ssh --postgres-user postgres --postgres-password postgres
```

```
lein run test-all -w append --test-count 10  --max-txn-length 10 --txn-num 1000 --max-writes-per-key 16 --concurrency 50 -r 200 --isolation serializable --time-limit 120 --nemesis none --existing-postgres --node localhost --no-ssh --postgres-user postgres --postgres-password postgres
```



## Test



如上面的代码可以跑通，则可以使用已有脚本测试不同参数下的PostgreSQL

#### 1、测试append

##### 1.1  ``run.py``

在文件中修改``workload``为append。

在文件中修改``max_txn_length_set``，可测试多组``max_txn_length``（最大事务长度）。

##### 1.2  ``batchrun.py``（会调用``run.py``)

在文件中修改``txn_number``，可测试多组``txn_number``（测试事务的数量）。

在文件中修改``max_writes_per_key``，可测试多组``max_writes_per_key``（每键最大写入次数）。

在文件中修改t``test_cnt``，决定每组参数测试的次数。

使用``python3 batchrun.py``来开始测试。



#### 2、测试mini

##### 2.1  ``run_mini.py``

在文件中修改``workload``为``mini``。

在文件中修改``max_txn_length_set``，可测试多组``max_txn_length``（最大事务长度）。

##### 2.2  ``batchrun_mini.py``（会调用``run_mini.py``)

在文件中修改``txn_number``，可测试多组``txn_number``（测试事务的数量）。

在文件中修改``max_writes_per_key``，可测试多组``max_writes_per_key``（每键最大写入次数）。

在文件中修改``test_cnt``，决定每组参数测试的次数。

使用``python3 batchrun_mini.py``来开始测试。



## Options



请参阅 ``lein run test --help`` 了解所有选项。

 ``--txn_number NUM `` 规定本次测试事务的数量。

`` --test-count NUM `` 规定本次测试的次数。

 ``--max-txn-length NUM `` （最大事务长度）和 `` max-writes-per-key `` （每键最大写入次数）规定了事务的最大大小，以及在选择新键之前写入任何单个键的次数。

``--nemesis FAULTS``以逗号分隔的故障列表，而 ``--nemesis-interval SECONDS`` 则 控制每类故障的nemesis操作之间的大致间隔时间。

``--rate -r NUM`` 控制 Jepsen 每秒尝试执行的操作次数上限。

``-v VERSION ``控制我们安装和测试的 PostgreSQL  版本。

``-w WORKLOAD`` 告诉 Jepsen 要运行的工作负载：例如 ``append``，``mini``。

``--concurrency NUM`` 告诉 Jepsen 要运行的并发度。

``--isolation`` 告诉 Jepsen 测试的隔离级别。

``--time-limit NUM`` 控制 Jepsen的运行时间。

``--postgres-user  ``  PostgreSQL数据库的用户名。

``--postgres-password`` PostgreSQL数据库的密码。



## License


Copyright © 2020 Jepsen, LLC

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License, v. 2.0 are satisfied: GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version, with the GNU Classpath Exception which is available at https://www.gnu.org/software/classpath/license.html.
