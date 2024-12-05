(ns jepsen.mongodb.merged
  "Combined mini and core workloads"
  (:require [clojure.tools.logging :refer [info warn]]
            [clojure.string :as str]
            [dom-top.core :refer [disorderly with-retry assert+]]
            [jepsen [client :as client]
                     [generator :as gen]
                     [checker :as checker]
                     [util :as util :refer [parse-long]]
                     [store :as store]]
            [jepsen.tests.bank :as bank]
            [jepsen.tests.cycle.append :as append]
            [elle.rw-register :as wr]
            [elle.txn :as et]
            [next.jdbc :as j]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql.builder :as sqlb]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.pprint :as pprint]
            [jepsen.mongodb.list-append :as la]
            [jepsen.mongodb.client :as c]
            [jepsen.mongodb.core :as mini])
  (:import (com.mongodb.client.model Filters
                                     UpdateOptions)))

;; Core functions and definitions
(def default-table-count 3)
(def max-diff 5)
(def counter (atom 1))

(defn table-name [i] (str "txn" i))

(defn table-for [table-count k] (table-name (mod (hash k) table-count)))

(defn insert! [conn test txn? table k e]
  (try
    (info (if txn? "" "not") "in transaction")
    (when txn? (j/execute! conn ["savepoint upsert"]))
    (info :insert (j/execute! conn [(str "insert into " table " (id, sk, val) values (?, ?, ?)") k k e]))
    (when txn? (j/execute! conn ["release savepoint upsert"]))
    true
    (catch org.postgresql.util.PSQLException e
      (if (re-find #"duplicate key value" (.getMessage e))
        (do (info (if txn? "txn") "insert failed: " (.getMessage e))
            (when txn? (j/execute! conn ["rollback to savepoint upsert"]))
            false)
        (throw e)))))

(defn update! [conn test table k e]
  (let [res (-> conn
                (j/execute-one! [(str "update " table " set val = ? where id = ?") e k]))]
    (info :update res)
    (-> res :next.jdbc/update-count pos?)))

(defn mop! [conn test txn? [f k v] values]
  (let [table-count (:table-count test default-table-count)
        table (table-for table-count k)]
    (Thread/sleep (rand-int 10))
    [f k (case f
           :r (let [r (j/execute! conn [(str "select (val) from " table " where id = ?") k]
                                 {:builder-fn rs/as-unqualified-lower-maps})]
                (when-let [v (:val (first r))]
                  (if (nil? v) nil (long v))))
           :w (let [new-value v]
                (or (update! conn test table k new-value)
                    (insert! conn test txn? table k new-value)
                    (update! conn test table k new-value)
                    (throw+ {:type ::homebrew-upsert-failed :key k :element v}))
                (long new-value)))]))

(defn workload [opts]
  (-> (mini/workload opts)
      (assoc :client (Client. nil))))

;; Mini-specific functions
(defn apply-mop! [test db session [f k v :as mop]]
  (let [coll (c/collection db la/coll-name)]
    (case f
      :r      [f k (:value (c/find-one coll session k))]
      :w (let [filt (Filters/eq "_id" k)
               doc  (c/->doc {:$set {:value v}})
               opts (.. (UpdateOptions.) (upsert true))
               res  (if session
                      (.updateOne coll session filt doc opts)
                      (.updateOne coll filt doc opts))]
           mop))))

(defrecord Client [conn]
  client/Client
  (open! [this test node] (assoc this :conn (c/open node test)))
  (setup! [this test] (la/create-coll! test conn))
  (invoke! [this test wtf]
    (let [txn (:value wtf)]
      (c/with-errors wtf
        (util/timeout 5000 (assoc wtf :type :info :error :timeout)
          (let [db   (c/db conn la/db-name test)
                txn' (with-open [session (c/start-session conn)]
                       (let [opts (la/txn-options test txn)
                             body (c/txn (mapv (partial apply-mop! test db session) (:value wtf)))]
                         (.withTransaction session body opts)))
                ooo {:index (:index wtf)
                     :time (:time wtf)
                     :process (:process wtf)
                     :f (:f wtf)
                     :type :ok
                     :value txn'}]
            ooo)))))
  (teardown! [this test])
  (close! [this test] (c/close! conn)))

;; Add other functions from the core and mini files as needed

