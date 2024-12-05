import os 
import subprocess
import shutil
import time
# import edn_format # pip install edn_format
import sys
import re

# === config ===

root_path = os.path.dirname(os.path.abspath(__file__))

workload = 'mini' # 'append' or 'mini' or 'wr'
assert workload == 'list-append' or workload == 'mini' or workload == 'wr'

# max_txn_length_set = [1, 2, 4, 6, 8, 10, 12, 16, 20]
max_txn_length_set = [4]
# max_txn_length_set = [2, 4, 6, 8, 10, 12]
test_count = int(sys.argv[2])
config = {
  '--nodes-file': '/root/nodes',
  '-r': '500',
  '--version': '4.2.6',
  '--max-writes-per-key': sys.argv[3],
  # '--concurrency': '10',
  '--txn-num': sys.argv[1],
  '--time-limit': '1440000', # infinity
  '--test-count': '1', # jepsen will stop upon detecting a bug
  # skip '--max-txn-length'
  '--nemesis': 'partition',
  '--read-concern': 'majority',
  '--write-concern': 'majority',
  '--txn-read-concern': 'snapshot',
  '--txn-write-concern': 'majority'
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

    # if result.returncode != 0: # detect a bug
    #   bug_count += 1

    log_path = os.path.join(root_path, 'store', f'mongodb-4.2.6-{workload}')
    check_time = 0
    abort_rate = 0
    for log_dir in os.listdir(log_path):
      if log_dir == 'latest':
        continue
      # only 1 log_path
      result_path = os.path.join(log_path, log_dir, 'results.edn')
      log_path = os.path.join(root_path, 'store', f'mongodb-4.2.6-mini/latest/history.edn')
      output_path = os.path.join(root_path, 'store', f'mongodb-4.2.6-mini/latest/1.txt')
      jar_path = os.path.join(root_path, 'PolySI-1.0.0-SNAPSHOT.jar')
      check_path = 'store/mongodb-4.2.6-mini/latest/1.txt'
      command = ["lein", "run", log_path, output_path]
      command_2 = ["java", "-jar", jar_path, "auditMini", "-t", "TEXT", check_path]
      with open(result_path, 'r') as file:
          #  data = edn_format.loads(file.read())
           content = file.read()

      # check_time_ms_match = re.search(r':check-time-ms\s+(\d+)', content)
      count_match = re.search(r':count\s+(\d+)', content)
      ok_count_match = re.search(r':ok-count\s+(\d+)', content)

      # check_time = int(check_time_ms_match.group(1)) if check_time_ms_match else None
      n_count = int(count_match.group(1)) if count_match else None
      ok_count = int(ok_count_match.group(1)) if ok_count_match else None
      abort_rate = (n_count - ok_count) / ok_count
      subprocess.run(command, cwd=os.path.join(root_path, 'edn2txt-master'), check=True)
      result = subprocess.run(command_2, capture_output=True, text=True)
      entire_experiment_time = re.search(r"ENTIRE_EXPERIMENT:\s*(\d+)ms", result.stderr)
      status = re.search(r"\[\[\[\[\s*(REJECT|ACCEPT)\s*\]\]\]\]", result.stderr)
      check_time = int(entire_experiment_time.group(1)) if entire_experiment_time else None
      bug = 'Y' if status and status.group(1) == 'REJECT' else 'N'
      if bug == 'Y': # detect a bug
           bug_count += 1
      
    elapsed_time = int((end_time - start_time) * 1000)

    print(f'Round: {_ + 1}, Time: {elapsed_time}ms, Checking Time: {check_time}ms, Bug: {bug} , N_Count: {n_count} , OK_Count: {ok_count}, Abort Rate: {abort_rate:.3f}')
    total_time += elapsed_time
    total_check_time += check_time
  
  print('=======================')
  print(f'Total Run Time: {total_time}ms, Total Checking Time: {total_check_time}ms')
  print(f'Bug / Total: {bug_count}/{test_count}')  
  print('')
