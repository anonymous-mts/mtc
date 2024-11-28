log_file = "log_run_cnt100_txn3000_perkey8.log"
cnt = 100
csv_content = "test_cnt, txn_num, per_key, txn_len, run_time, check_time, bug\n"
txn_number = 3000
perkey = 6
reader = open(log_file, "r")
lines = reader.readlines()
reader.close()
end = False
sum_rate = 0
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
        csv_content += f"{cnt}, {txn_number}, {perkey}, {txn_len}, {run_time}, {check_time}, {bug}, {abort}\n"
        writer = open("result.csv", "w")
        writer.write(csv_content)
        writer.close()
        end = False
        cur_cnt = 0
        sum_rate = 0