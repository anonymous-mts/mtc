# DBCOP adapts for use with the Jepsen workload generator.

## 1. Generate test samples in bincode format.

```bash
➜  dbcdc-runner git:(main) ✗ dbcop generate -d /tmp/generate/ -n 25 -t 100 -e 20 -v 1000 --nhist 10
➜  dbcdc-runner git:(main) ✗ cd /tmp/generate 
➜  generate ls
hist-00000.bincode  hist-00001.bincode  hist-00002.bincode  hist-00003.bincode  hist-00004.bincode  hist-00005.bincode  hist-00006.bincode  hist-00007.bincode  hist-00008.bincode  hist-00009.bincode
```

## 2. Generate test samples in JSON format.

```bash
➜  generate dbcop convert -d /tmp/generate --from bincode
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00002.bincode" to "hist-00002.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00008.bincode" to "hist-00008.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00001.bincode" to "hist-00001.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00004.bincode" to "hist-00004.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00006.bincode" to "hist-00006.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00005.bincode" to "hist-00005.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00007.bincode" to "hist-00007.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00003.bincode" to "hist-00003.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00000.bincode" to "hist-00000.json"
[2023-07-20T17:41:50Z INFO  dbcop] Converting "hist-00009.bincode" to "hist-00009.json"
```

## 3. 在脚本中加入参数
案例可见 ./databases/dgraph/run-dgraph.sh
```bash
--dbcop-workload-path /tmp/generate/hist-00000.json # Specify the address of DBCOP. \ 
--dbcop-workload # Enable DBCOP mode.
```
