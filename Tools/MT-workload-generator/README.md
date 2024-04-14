# MT Workload Generator

The MT Workload Generator is a tool designed to generate mini-transaction workloads, where each transaction is a mini-transaction. By carefully structuring these mini-transactions, we aim to reduce execution overhead during history generation and improve the efficiency of isolation level checking algorithms.

For SSER, we have developed a generator that creates synthetic lightweight transaction histories, where each transaction represents a lightweight transaction (a specific type of mini-transaction).

For SER/SI, we have implemented a generator specifically tailored for mini-transaction workloads. By executing these workloads on PostgreSQL 14.7 under either the *REPEATABLE READ* or *SERIALIZABLE* isolation level, we obtain real-world transaction histories for experimental purposes.

For non-essential functionalities such as parsing configuration files and database connectivity, we have reused code from [CobraBench](https://github.com/DBCobra/CobraBench).
