# Mini-Transaction Checker (MTC)

The Mini-Transaction Checker (MTC) is a tool designed to verify the SSER/SER/SI isolation level guarantees for mini-transaction histories. It utilizes **polynomial-time** algorithms outlined in our research paper. Our experiments, available in the [Data](../../Data/) directory, show significant efficiency improvements over [existing tools](../baseline-checkers/).

MTC-SSER is tailored for verifying SSER of lightweight transaction histories (a specific instance of mini-transactions) written in Go. For non-essential modules, such as parsing history files, it leverages code from [porcupine](https://github.com/anishathalye/porcupine).

MTC-SER-SI is focused on verifying SER/SI for mini-transaction histories. Similar to MTC-SSER, it utilizes code from [PolySI](https://github.com/amnore/PolySI) for non-essential functionalities like parsing history files.
