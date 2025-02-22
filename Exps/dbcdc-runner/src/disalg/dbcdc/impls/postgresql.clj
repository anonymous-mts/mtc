(ns disalg.dbcdc.impls.postgresql
  "Helper functions for interacting with PostgreSQL clients."
  (:require [clojure.tools.logging :refer [info warn]]
            [dom-top.core :refer [with-retry]]
            [jepsen [client :as client]
             [util :as util]]
            [next.jdbc :as j]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql.builder :as sqlb]
            [slingshot.slingshot :refer [try+ throw+]]
            [wall.hack :as wh]
            [disalg.dbcdc.utils.cfg :refer [read-spec]]
            [disalg.dbcdc.impls
             [postgresql :as pg]
             [mysql :as mysql]
             [oracle :as oracle]]
            [jepsen.db :as db])
  (:import (java.sql Connection)))

(defn set-transaction-isolation!
  "Sets the transaction isolation level on a connection. Returns conn."
  [conn level]
  (.setTransactionIsolation
   conn
   (case level
     :serializable        Connection/TRANSACTION_SERIALIZABLE
     :snapshot-isolation  Connection/TRANSACTION_REPEATABLE_READ
     :repeatable-read     Connection/TRANSACTION_REPEATABLE_READ
     :read-committed      Connection/TRANSACTION_READ_COMMITTED
     :read-uncommitted    Connection/TRANSACTION_READ_UNCOMMITTED))
  conn)

(defn isolation-mapping
  [level]
  (case level
    :serializable       :serializable
    :snapshot-isolation :repeatable-read
    :repeatable-read    :repeatable-read
    :read-committed     :read-committed
    :read-uncommitted   :read-uncommitted))

(defn check-in-txn
  [^Connection conn]
  (try
    (j/execute! conn ["SAVEPOINT upsert"])
    (info :set-iso-level (set-transaction-isolation! conn :snapshot-isolation))
    (j/execute! conn ["RELEASE SAVEPOINT upsert"])
    false
    (catch Exception e
      (do (info "In transaction well " (.getMessage e))
          (j/execute! conn ["ROLLBACK TO SAVEPOINT upsert"])
          true))))

(defn create-table
  [^Connection conn table]
  (let [drop-stmt (str "DROP TABLE IF EXISTS " table)
        create-stmt (str "CREATE TABLE IF NOT EXISTS " table
                         " (key INT NOT NULL PRIMARY KEY, val INT NOT NULL)")]
    (j/execute-one! conn [drop-stmt])
    (j/execute-one! conn [create-stmt])))

(defn create-varchar-table
  [^Connection conn table]
  (let [drop-stmt (str "DROP TABLE IF EXISTS " table)
        create-stmt (str "CREATE TABLE IF NOT EXISTS " table
                         " (key VARCHAR(16) NOT NULL PRIMARY KEY, val VARCHAR(16) NOT NULL)")]
    (j/execute-one! conn [drop-stmt])
    (j/execute-one! conn [create-stmt])))

(defn read
  [^Connection conn table key]
  (let [res (j/execute-one! conn
;                            [(str "select val from " table " where key = ?") key]
                            [(str "SELECT val FROM " table " WHERE key = " key)]
                            {:builder-fn rs/as-unqualified-lower-maps})]
    ;; (info "op: read (" key "), res: " res ", state :"
    ;;       (j/execute! conn ["SELECT current_setting('transaction_isolation')"])
    ;;       "in-txn? " (check-in-txn conn))
    (when-let [v (:val res)]
      (if (string? v)
        (long (Long/parseLong v))
        (long v)))))

(defn read-varchar
  [^Connection conn table key]
  (let [res (j/execute-one! conn
                            [(str "SELECT val FROM " table " WHERE key = '" key "'")]
                            {:builder-fn rs/as-unqualified-lower-maps})]
    (info "pg-read" res)
    (when-let [v (:val res)]
      (if (string? v)
        (long (Long/parseLong v))
        (long v)))))

(defn write
  [^Connection conn table key value]
  (let [res (j/execute-one!
             conn
;             [(str "insert into " table " as t"
;                   " (key, val) values (?, ?)"
;                   " on conflict (key) do update set"
;                   " val = ? where t.key = ?")
;              key value value key]
             [(str "INSERT INTO " table " AS t"
                   " (key, val) VALUES (" key ", " value ")"
                   " ON CONFLICT (key) DO UPDATE SET"
                   " val = " value " WHERE t.key = " key)])]
    ;; (info "op: write (" key value "), res: " res ", state :"
    ;;       (j/execute! conn ["SELECT current_setting('transaction_isolation')"])
    ;;       "in-txn? " (check-in-txn conn))

    (when-not (pos? (res :next.jdbc/update-count))
      (throw+ {:type ::write-but-not-take-effect
               :key  key
               :val  value}))))

(defn write-varchar
  [^Connection conn table key value]
  (let [res (j/execute-one!
             conn
             [(str "INSERT INTO " table " AS t"
                   " (key, val) VALUES ('" key "', '" value "')"
                   " ON CONFLICT (key) DO UPDATE SET"
                   " val = '" value "' WHERE t.key = '" key "'")])]
    (info "pg-write" res)
    (when-not (pos? (res :next.jdbc/update-count))
      (throw+ {:type ::write-but-not-take-effect
               :key  key
               :val  value}))))

(defn insert-savepoint
  "Performs an initial insert of a key with initial element e. Catches
  duplicate key exceptions, returning true if succeeded. If the insert fails
  due to a duplicate key, it'll break the rest of the transaction, assuming
  we're in a transaction, so we establish a savepoint before inserting and roll
  back to it on failure."
  [conn table k v]
  (try
    (j/execute! conn ["SAVEPOINT upsert"])
    (info :insert (j/execute! conn
                              [(str "INSERT INTO " table " (key, val)"
                                    " VALUES ('" k "','" v "')")]))
    (j/execute! conn ["RELEASE SAVEPOINT upsert"])
    true
    (catch Exception e
      (if (re-find #"duplicate key value" (.getMessage e))
        (do (info "insert failed: " (.getMessage e))
            (j/execute! conn ["ROLLBACK TO SAVEPOINT upsert"])
            false)
        (throw e)))))

(defn update-savepoint
  "Performs an update of a key k, adding element e. Returns true if the update
  succeeded, false otherwise."
  [conn table k v]
  (let [query (str "UPDATE " table " SET val = '" v "' WHERE key = '" k "'")
        res (-> conn
                (j/execute-one! [query]))]
    (info :update res)
    (-> res
        :next.jdbc/update-count
        pos?)))

(defn write-varchar-savepoint
  [^Connection conn table key value]
  (or (update-savepoint conn table key value)
                   ; No dice, fall back to an insert
      (insert-savepoint conn table key value)
                   ; OK if THAT failed then we probably raced with another
                   ; insert; let's try updating again.
      (update-savepoint conn table key value)
                   ; And if THAT failed, all bets are off. This happens even
                   ; under SERIALIZABLE, but I don't think it technically
                   ; VIOLATES serializability.
      (throw+ {:type     ::homebrew-upsert-failed
               :key      key
               :element  value})))