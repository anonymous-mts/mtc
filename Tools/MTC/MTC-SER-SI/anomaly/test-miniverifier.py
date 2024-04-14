import subprocess
import pytest

JAR_PATH = "/home/ubuntu/PolySI/build/libs/PolySI-1.0.0-SNAPSHOT.jar"


def mini_verify_and_capture_output(text_history_path):
    command = ["java", "-jar", JAR_PATH, "auditMini", "-t=TEXT"] + [text_history_path]
    process = subprocess.Popen(
        command, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
    )
    stdout, stderr = process.communicate()
    return stdout, stderr, process.returncode


@pytest.mark.parametrize(
    "text_history_path, expected_stdout_or_stderr",
    [
        ("/home/ubuntu/PolySI/anomaly/causality-violation.txt", "REJECT"),
        ("/home/ubuntu/PolySI/anomaly/fractured-reads.txt", "REJECT"),
        ("/home/ubuntu/PolySI/anomaly/long-fork.txt", "REJECT"),
        ("/home/ubuntu/PolySI/anomaly/lost-update.txt", "REJECT"),
        ("/home/ubuntu/PolySI/anomaly/session-guarantee.txt", "REJECT"),
        ("/home/ubuntu/PolySI/anomaly/write-skew.txt", "ACCEPT"),
        # Add more test cases as needed
    ],
)
def test_mini_execution(text_history_path, expected_stdout_or_stderr, capsys):
    stdout, stderr, return_code = mini_verify_and_capture_output(text_history_path)

    pass_test = (
        expected_stdout_or_stderr in stdout or expected_stdout_or_stderr in stderr
    )

    assert pass_test
