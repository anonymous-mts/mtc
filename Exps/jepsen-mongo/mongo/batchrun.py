import os
import subprocess
txn_number = [3000]
# txn_number = [1000]
max_writes_per_key = [10]
# max_writes_per_key = [6,8]
test_cnt = [10]
csv_content = "test_cnt, txn_num, per_key, max_txn_len, run_time, check_time, bug, n_count, ok_count, avg_abort\n"


for cnt in test_cnt:
    for txn in txn_number:
        for perkey in max_writes_per_key:
                log_file = f"log_run_cnt{cnt}_txn{txn}_perkey{perkey}.log"
                print(f"Batch run with cnt_num: {cnt}, txn_num: {txn}, perkey: {perkey}, output: {log_file}")
                with open(log_file, "w")  as f:
                    # command = f"python3 run.py {txn} {cnt} {perkey}"
                    command = f"python3 run_mini.py {txn} {cnt} {perkey}"
                    process = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True, bufsize=1)   
                    for line in iter(process.stdout.readline, ''):
                        print(line, end='')  # Output to the console
                        f.write(line)        # Write to the file
                        f.flush()            # Flush to write to the file immediately
                    try:
                        process.stdout.close()
                        process.wait()
                    except:
                        pass
                reader = open(log_file, "r")
                lines = reader.readlines()
                reader.close()
                end = False
                sum_rate = 0.0
                cur_cnt = 0
                for l in lines:
                    if "Run with txn_num" in l:
                        txn_num = l.split(": ")[1].split(",")[0]
                    if "max_txn_length" in l:
                        txn_len = l.split(": ")[1].strip()
                    if "Total Run Time" in l:
                        run_time = l.split(": ")[1].split("ms")[0]
                    if "Total Checking Time" in l:
                        newl = l[l.find("Total Checking Time"):]
                        check_time = newl.split(": ")[1].split("ms")[0]
                    if "N_Count: " in l:
                        newl = l[l.find("N_Count: "):]
                        # N_Count = int(newl.split(": ")[1])
                        N_Count = int(newl.split(": ")[1].split(",")[0].strip())
                    if "OK_Count: " in l:
                        newl = l[l.find("OK_Count: "):]
                        # OK_Count = int(newl.split(": ")[1])
                        OK_Count = int(newl.split(": ")[1].split(",")[0].strip())
                    if "Abort Rate: " in l:
                        newl = l[l.find("Abort Rate: "):]
                        cur_rate = float(newl.split(": ")[1])
                        cur_cnt += 1
                        sum_rate += cur_rate
                    if "Bug / Total" in l:
                        newl = l[l.find("Bug / Total"):]
                        bug = newl.split(": ")[1].strip()
                        end = True
                    if end == True:
                        abort = sum_rate / cur_cnt
                        assert(cnt == cur_cnt)
                        csv_content += f"{cnt}, {txn_number}, {perkey}, {txn_len}, {run_time}, {check_time}, {bug}, {N_Count}, {OK_Count}, {abort}\n"
                        writer = open("result.csv", "w")
                        writer.write(csv_content)
                        writer.close()
                        end = False
                        cur_cnt = 0
                        sum_rate = 0.0