# DB-CDC-PostgreSQL



PostgreSQL 测试

## Usage



#### 1、进入dbcdcd-runner，配置数据源

修改`resources/db-config.edn`，配置文件，增加 `:postgresql` 键：

```clojure
:postgresql {:dbtype    "postgresql"
              :host      "localhost"
              :port      5432
              :user      "postgres"
              :password  "postgres"
              :dbname    "test"
              :sslmode   "disable"}
```

#### 2、进入dbcdcd-runner文件夹，运行命令测试pg

```
cd dbcdc-runner
```

```
lein run test --key-count 100 --max-writes-per-key 10 --concurrency 50 --rate 500 --time-limit 1440000 --max-txn-length 10 --txn-num 1000 --test-count 10 --isolation serializable --nemesis none --existing-postgres --node localhost --no-ssh --database postgresql
```



## Test



如上面的代码可以跑通，则可以使用已有脚本测试不同参数下的PostgreSQL

#### 1、测试rw

##### 1.1  ``run.py``

在文件中修改``workload``为rw。

在文件中修改``max_txn_length_set``，可测试多组``max_txn_length``（最大事务长度）。

##### 1.2  ``batchrun.py``（会调用``run.py``)

在文件中修改``txn_number``，可测试多组``txn_number``（测试事务的数量）。

在文件中修改``max_writes_per_key``，可测试多组``max_writes_per_key``（每键最大写入次数）。

在文件中修改t``test_cnt``，决定每组参数测试的次数。

使用``python3 batchrun.py``来开始测试。



## Options



请参阅 ``lein run test --help`` 了解所有选项。

``--key-count NUM `` 规定本次测试键的数量。

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

``--database   ``告诉 Jepsen 测试的数据库。

