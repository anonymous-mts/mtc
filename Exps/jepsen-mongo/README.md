# jepsen.mongodb



MongoDB 测试，在 Debian Buster 上运行。多分片测试需要 5个节点.

## Usage



#### 1、进入docker目录下，启动五个数据节点和一个控制节点

```
cd mini-testing-artifact/Exps/jepsen-mongo/docker
sudo ./bin/up --dev
```

#### 2、再开一个 shell，进入docker目录下，进入控制节点

```
cd mini-testing-artifact-main/Exps/jepsen-mongo/docker
sudo ./bin/console  
```

#### 3、进入mini目录下，构建mini

```
cd ../mini
lein install
```

#### 4、进入mongodb文件夹，运行命令测试mongo

```
cd ../mongo
```

```
lein run test-all  --nodes-file /root/nodes --version 4.2.6 --max-writes-per-key 8 --concurrency 10 --txn-num 1000 -r 500 --time-limit 1440000 --test-count 1 --max-txn-length 4 --nemesis partition --read-concern majority --write-concern majority --txn-read-concern snapshot --txn-write-concern majority -w list-append
```

```
lein run test-all  --nodes-file /root/nodes --version 4.2.6 --max-writes-per-key 8 --concurrency 10 --txn-num 1000 -r 500 --time-limit 1440000 --test-count 1 --max-txn-length 4 --nemesis partition --read-concern majority --write-concern majority --txn-read-concern snapshot --txn-write-concern majority -w mini
```

```
lein run test-all  --nodes-file /root/nodes --version 4.2.6 --max-writes-per-key 8 --concurrency 10 --txn-num 1000 -r 500 --time-limit 1440000 --test-count 1 --max-txn-length 4 --nemesis partition --read-concern majority --write-concern majority --txn-read-concern snapshot --txn-write-concern majority -w wr
```



## Test



如上面的代码可以跑通，则可以使用已有脚本测试不同参数下的mongodb

#### 1、测试wr/list-append

##### 1.1  ``run.py``

在文件中修改``workload``，决定测试哪个``workload``。

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

``--hidden NUM `` 允许你将每个副本集中的第一个  ``NUM `` 节点指定为隐藏副本。

 ``--[no-]journal ``允许你强制打开或关闭写关注的日志标记；否则将保留客户端默认值。

 ``--lazyfs ``将Mongo的数据目录挂载在lazyfs文件系统上，这意味着进程杀不仅会杀死进程，还会丢失未同步的写入。这对模拟断电很有帮助。

 ``--local-proxy PATH/TO/LOCAL/BIN `` 在本地控制节点上运行一个自定义二进制文件，并将客户端请求路由到它，而不是直接路由到远程节点。 ``--proxy ``的作用与此相同，但会在每个数据库节点上上传并运行代理。该功能是为一个代理不公开的 Jepsen 客户端开发的，但它应该适用于任何使用相同参数的代理。详情请参阅 `` jepsen.mongodb.db.local-proxy/start! ``

 ``--max-txn-length ``（最大事务长度）和 `` max-writes-per-key `` （每键最大写入次数）规定了事务的最大大小（例如在 `` list-append `` 工作负载中），以及在选择新键之前写入任何单个键的次数。

``--nemesis FAULTS``以逗号分隔的故障列表，而 ``--nemesis-interval SECONDS`` 则 控制每类故障的nemesis操作之间的大致间隔时间。

即使在只读事务中，这些测试也会自动设置写关注--这与 MongoDB 的文档一致。不过，这样做并不直观，许多 Mongo 事务指南都忽略了这一点。使用`` --no-read-only-write-concern``（不只读写关注）会做一件显而易见的错事，即不为只读事务提供写关注。这会导致快照隔离违规。

``--rate HZ`` 控制 Jepsen 每秒尝试执行的操作次数上限。

``--read-concern CONCERN`` 和``--txn-read-concern CONCERN`` 分别为单个操作和 Mongo 事务设置读取关注。``--write-concern``和``--txn-write-concern``对写关注点做同样的设置。如果省略，则使用完全不安全的客户端默认值。

``--read-preference PREF`` 控制客户端是否尝试从主程序、次程序等读取数据。

``--[no-]retry-writes``允许你明确选择是否在客户端启用重试写入。请注意，对于某些事务特性，Mongo 会忽略此设置。

如果设置了``--sharded``，则会运行多个副本集，每个副本集有 3 个副本。前三个节点组成一个副本集，用于配置。后 3 个节点组成第一个数据分片，第 3 个节点组成第二个数据分片，依此类推--因此，运行分片测试至少需要 9 个节点。

只有当逻辑 Jepsen 事务包含多个操作（例如，一个读和一个写或三个写）时，该测试才会执行 Mongo 事务。单次读取和单次写入会直接执行，而不会执行 Mongo 事务。使用`` --singleton-txns`` 可以强制为每个 Jepsen 事务执行 Mongo 事务。

``-v VERSION ``控制我们安装和测试的 MongoDB 版本。

``-w WORKLOAD`` 告诉 Jepsen 要运行的工作负载：例如 ``list-append``，``mini``，``wr``



## License



Copyright © 2020 Jepsen, LLC

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License, v. 2.0 are satisfied: GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version, with the GNU Classpath Exception which is available at https://www.gnu.org/software/classpath/license.html.
