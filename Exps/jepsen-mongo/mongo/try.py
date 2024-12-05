import subprocess
import os 
import re
root_path = os.path.dirname(os.path.abspath(__file__))
# 设置目标目录
# working_directory = "/home/ubuntu/mini-testing-artifact/Exps/jepsen-mongo/mongodb/edn2txt-master"

log_path = os.path.join(root_path, 'store', f'mongodb-4.2.6-mini/latest/history.edn')
output_path = os.path.join(root_path, 'store', f'mongodb-4.2.6-mini/latest/1.txt')
# log_path = os.path.join(root_path,  'store', 'mongodb-4.2.6-mini', 'latest', 'history.edn')
# output_path = os.path.join(root_path, 'store', 'mongodb-4.2.6-mini', 'latest', 'history.txt')


jar_path = os.path.join(root_path, 'PolySI-1.0.0-SNAPSHOT.jar')
check_path = 'store/mongodb-4.2.6-mini/latest/1.txt'
# 设置命令和路径
command = [
    "lein", "run", log_path, output_path
]

command_2 = [
    "java", "-jar", jar_path, "auditMini", "-t", "TEXT", check_path
]

# 运行命令
try:
    subprocess.run(command, cwd=os.path.join(root_path, 'edn2txt-master'), check=True)
    print("命令执行成功！")
    result = subprocess.run(command_2, capture_output=True, text=True)
    print(command_2)
    # 打印标准输出和标准错误
    print("标准输出:")
    print(result.stdout)
    print("标准错误:") 
    print(result.stderr)
except subprocess.CalledProcessError as e:
    print(f"命令执行失败: {e}")


def extract_info(data):
    entire_experiment_time = re.search(r"ENTIRE_EXPERIMENT:\s*(\d+)ms", data)
    status = re.search(r"\[\[\[\[\s*(REJECT|ACCEPT)\s*\]\]\]\]", data)
    
    check_time = entire_experiment_time.group(1) if entire_experiment_time else None
    bug = 'Y' if status and status.group(1) == 'REJECT' else 'N'
    
    return {'check_time': check_time, 'bug': bug}

result = extract_info(result.stderr)
print(result)