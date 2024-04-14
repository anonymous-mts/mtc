#!/usr/bin/python3
from operator import inv
from random import random, shuffle, sample
import sys


write = """{:type :invoke, :f :write, :value 0, :time 0, :process 4, :index 0}
{:type :ok, :f :write, :value 0, :time 1, :process 4, :index 1}"""

def gen_n_overlap(n, start_value, start_time, start_index, processes):
    invokes = [f"{{:type :invoke, :f :cas, :value [{i} {i+1}], :time time_placeholder, :process {processes[i - start_value]}, :index index_placeholder}}" 
        for i in range(start_value, start_value + n)]
    shuffle(invokes)
    oks = [f"{{:type :ok, :f :cas, :value [{i} {i+1}], :time time_placeholder, :process {processes[i - start_value]}, :index index_placeholder}}" 
        for i in range(start_value, start_value + n)]
    shuffle(oks)    
    reqs = invokes + oks
    overlaps = []
    for (i, req) in enumerate(reqs):
        index_ = req.replace('index_placeholder', str(start_index + i))
        time_ = index_.replace('time_placeholder', str(start_time + i))
        overlaps.append(time_)
    return overlaps, start_value + n, start_time + 2 * n, start_index + 2 * n

print(write)
n_clients = int(sys.argv[1])
n_txns = int(sys.argv[2])
ptg_concurrency = int(sys.argv[3])
n_overlap = n_clients * ptg_concurrency // 100
start_value, start_time, start_index = 0, 2, 2
while n_txns > 0:
    n_txns_gen = n_overlap if n_txns > n_overlap else n_txns
    overlaps, start_value, start_time, start_index = gen_n_overlap(n_txns_gen, start_value, start_time, start_index, sample(list(range(0, n_clients)), n_clients * ptg_concurrency // 100))
    for overlap in overlaps:
        print(overlap)
    n_txns -= n_overlap