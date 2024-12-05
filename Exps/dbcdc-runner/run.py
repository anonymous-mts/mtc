# import os 
# import subprocess
# import shutil
# import time
# import edn_format # pip install edn_format
# import sys
# import re

# # === config ===

# root_path = os.path.dirname(os.path.abspath(__file__))

# workload = 'rw' # 'append' or 'mini' or 'rw'
# assert workload == 'append' or workload == 'mini' or workload == 'rw'

# test_count = int(sys.argv[3])
# config = {
#   '--key-count': '100',
#   '--max-writes-per-key': '10',
#   '--concurrency': '50', 
#   '--rate': '500',
#   '--time-limit': '1440000', # infinity
#   '--max-txn-length' : sys.argv[1] ,
#   '--txn-num': sys.argv[2], # '1000'
#   '--test-count': '1', # jepsen will stop upon detecting a bug
#   '--isolation': 'serializable',
#   '--nemesis': 'none',
#   '--existing-postgres': '',
#   '--node': 'localhost',
#   '--no-ssh': '',
#   '--database': 'postgresql'
# }
# print(f"Run with max-txn-length: {sys.argv[1]}, txn-num: {sys.argv[2]}, test_count: {sys.argv[3]}")
# # === script body ===

# def reset_dir(path):
#   if os.path.exists(path):
#     if os.path.isdir(path):
#         shutil.rmtree(path)

# def read_edn_value(file_path, key):
#   with open(file_path, 'r') as file:
#     data = edn_format.loads(file.read())  # 解析 EDN 数据
#   value = data.get(edn_format.Keyword(key))
#   return value
      

# bug_count = 0
# total_time = 0
# total_check_time = 0
# for _ in range(test_count):
#   cmd = ['lein', 'run', 'test', '-w', workload]
#   for k, v in config.items():
#     cmd.append(k)
#     if v != '':
#       cmd.append(v)
    
#   reset_dir(os.path.join(root_path, 'store'))
    
#   start_time = time.time()
#   result = subprocess.run(cmd, capture_output=True, text=True)
#   end_time = time.time()

#   # if result != 0: # detect a bug
#   #   bug_count += 1
#   if result.returncode != 0: # detect a bug
#     bug_count += 1
#   # sys.exit(0)

#   log_path = os.path.join(root_path, 'store', f'dbcdcrw-postgresql')
#   check_time = 0
#   for log_dir in os.listdir(log_path):
#     if log_dir == 'latest':
#       continue
#       # only 1 log_path
#     result_path = os.path.join(log_path, log_dir, 'results.edn')
#     check_time = int(read_edn_value(result_path, 'check-time-ms'))
#   elapsed_time = int((end_time - start_time) * 1000)

#   print(f'Round: {_ + 1}, Time: {elapsed_time}ms, Checking Time: {check_time}ms, Bug: {"N" if result.returncode == 0 else "Y"}')
#   total_time += elapsed_time
#   total_check_time += check_time
  
# print('=======================')
# print(f'Total Run Time: {total_time}ms, Total Checking Time: {check_time}ms')
# print(f'Bug / Total: {bug_count}/{test_count}')  
# print('')


import os 
import subprocess
import shutil
import time
import sys
import re

# === config ===

root_path = os.path.dirname(os.path.abspath(__file__))

workload = 'rw' # 'append' or 'mini' or 'wr'
assert workload == 'list-append' or workload == 'mini' or workload == 'rw'

# max_txn_length_set = [1, 2, 4, 6, 8, 10, 12, 16, 20]
max_txn_length_set = [2, 4, 6, 8, 10, 12]
# max_txn_length_set = [4]
test_count = int(sys.argv[2])
config = {
  '--key-count': '100',
  '--max-writes-per-key': sys.argv[3],
  '--concurrency': '100', 
  '--rate': '500',
  '--time-limit': '1440000', # infinity
  # '--max-txn-length' : sys.argv[1] ,
  '--txn-num': sys.argv[1], # '1000'
  '--test-count': '1', # jepsen will stop upon detecting a bug
  '--isolation': 'serializable',
  '--nemesis': 'none',
  '--existing-postgres': '',
  '--node': 'localhost',
  '--no-ssh': '',
  '--database': 'postgresql'
}
print(f"Run with txn_num: {sys.argv[1]}, test_count: {sys.argv[2]}")
# === script body ===

def reset_dir(path):
  if os.path.exists(path):
    if os.path.isdir(path):
        shutil.rmtree(path)

# def read_edn_value(file_path, key):
#   with open(file_path, 'r') as file:
#     data = edn_format.loads(file.read())  
#   value = data.get(edn_format.Keyword(key))
#   return value
      

# bug_count = 0
# total_time = 0
# total_check_time = 0
for max_txn_length in max_txn_length_set:
  bug_count = 0
  total_time = 0
  total_check_time = 0 
  print(f'max_txn_length: {max_txn_length}')
  print('=======================')
  for _ in range(test_count):
    cmd = ['lein', 'run', 'test-all', '-w', workload]
    for k, v in config.items():
      cmd.append(k)
      if v != '':
        cmd.append(v)
    cmd.append('--max-txn-length')
    cmd.append(str(max_txn_length))
    
    reset_dir(os.path.join(root_path, 'store'))
    
    start_time = time.time()
    result = subprocess.run(cmd, capture_output=True, text=True)
    end_time = time.time()

    if result.returncode != 0: # detect a bug
      bug_count += 1

    log_path = os.path.join(root_path, 'store', f'dbcdcrw-postgresql')
    check_time = 0
    abort_rate = 0
    for log_dir in os.listdir(log_path):
      if log_dir == 'latest':
        continue
      # only 1 log_path
      result_path = os.path.join(log_path, log_dir, 'results.edn')
      with open(result_path, 'r') as file:
          #  data = edn_format.loads(file.read())
           content = file.read()

      # check_time = int(read_edn_value(result_path, 'check-time-ms'))

      # abort rate = (n_count - n_ok_count) / n_count 
      # what is info_count?
      # stats = read_edn_value(result_path, 'stats')
      # n_count = int(stats.get(edn_format.Keyword('count')))
      # ok_count = int(stats.get(edn_format.Keyword('ok-count')))


      # check_time = data.get(':check-time-ms')
      # n_count  = int(data.get(':stats', {}).get(':count'))
      # ok_count = int(data.get(':stats', {}).get(':ok-count'))

      check_time_ms_match = re.search(r':check-time-ms\s+(\d+)', content)
      count_match = re.search(r':count\s+(\d+)', content)
      ok_count_match = re.search(r':ok-count\s+(\d+)', content)

      check_time = int(check_time_ms_match.group(1)) if check_time_ms_match else None
      n_count = int(count_match.group(1)) if count_match else None
      ok_count = int(ok_count_match.group(1)) if ok_count_match else None
      abort_rate = (n_count - ok_count) / n_count
      # print(f"Check Time (ms): {check_time}")
      # print(f"Count: {n_count}")
      # print(f"OK Count: {ok_count}")
      # print(f"Abort Rate: {abort_rate}")
      
    elapsed_time = int((end_time - start_time) * 1000)

    print(f'Round: {_ + 1}, Time: {elapsed_time}ms, Checking Time: {check_time}ms, Bug: {"N" if result.returncode == 0 else "Y"} , N_Count: {n_count} , OK_Count: {ok_count}, Abort Rate: {abort_rate:.3f}')
    total_time += elapsed_time
    total_check_time += check_time
  
  print('=======================')
  print(f'Total Run Time: {total_time}ms, Total Checking Time: {total_check_time}ms')
  print(f'Bug / Total: {bug_count}/{test_count}')  
  print('')
