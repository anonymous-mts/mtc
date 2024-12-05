(ns jepsen.mongodb.wr
  "Performs random reads and writes of unique values. Constructs a dependency
  graph using realtime order (since dgraph is supposed to be linearizable) and
  via write-read edges, and looks for cycles in that graph."
  (:require [clojure.tools.logging :refer [info]]
            [clojure.core.reducers :as r]
            [jepsen.mongodb.list-append :as la]
            ;; [jepsen.mongodb.elle.core :as elle]
            [elle.core :as elle]
            [fipp.edn :refer [pprint]]
            [jepsen.mongodb [client :as c]]
            [jepsen [client :as client]
                    [util :as util :refer [timeout]]
                    [checker :as checker]
                    [util :as util]
                    [store :as store]]
            [jepsen.checker.timeline :as timeline]
            [jepsen.tests.cycle :as cycle]
            [jepsen.tests.cycle.wr :as wr])
  (:import (com.mongodb.client.model Filters
                                     UpdateOptions)))

(defn apply-mop!
  "Applies a transactional micro-operation to a connection."
  [test db session [f k v :as mop]]
  (let [coll (c/collection db la/coll-name)]
    ;(info (with-out-str
    ;        (println "db levels")
    ;        (prn :sn-rc ReadConcern/SNAPSHOT)
    ;        (prn :ma-rc ReadConcern/MAJORITY)
    ;        (prn :db-rc (.getReadConcern db))
    ;        (prn :ma-wc WriteConcern/MAJORITY)
    ;        (prn :db-wc (.getWriteConcern db))))
    (case f
      :r      [f k (:value (c/find-one coll session k))]
      :w (let [filt (Filters/eq "_id" k)
               doc  (c/->doc {:$set {:value v}})
               opts (.. (UpdateOptions.) (upsert true))
               res  (if session
                      (.updateOne coll session filt doc opts)
                      (.updateOne coll filt doc opts))]
                ;(info :res res)
           mop))))

(defrecord Client [conn]
  client/Client
  (open! [this test node]
    (assoc this :conn (c/open node test)))

  (setup! [this test]
    (la/create-coll! test conn))

  (invoke! [this test op]
    (let [txn (:value op)]
      (c/with-errors op
        (timeout 5000 (assoc op :type :info, :error :timeout)
          (let [db   (c/db conn la/db-name test)
                txn' (if (and (<= (count txn) 1)
                              (not (:singleton-txns test)))
                       ; We can run without a transaction
                       [(apply-mop! test db nil (first txn))]

                       ; We need a transaction
                       (with-open [session (c/start-session conn)]
                         (let [opts (la/txn-options test txn)
                               body (c/txn
                                      ;(info :txn-begins)
                                      (mapv (partial apply-mop!
                                                     test db session)
                                            (:value op)))]
                           (.withTransaction session body opts))))]
            (assoc op :type :ok, :value txn'))))))

  (teardown! [this test])

  (close! [this test]
    (c/close! conn)))

(defn noob-checker
  []
  (reify checker/Checker
    (check [_ _ _ _]
      {:valid? true})))



;; (defn workload
;;   "Stuff you need to build a test!"
;;   [opts]
;;   {:client (Client. nil)
;;    :checker   (checker/compose
;;                 {:wr (wr/checker {:wfr-keys?           true
;;                                   :sequential-keys?    true
;;                                   :anomalies           [:G0 :G1c :G-single :G1a
;;                                                         :G1b :internal]})
;;                  ;:timeline (timeline/html)})
;;                  })
;;    :checker (wr/checker)
;;    :generator (wr/gen {:key-count  4
;;                        :min-length 2
;;                        :max-length 4
;;                        :max-writes-per-key 16})})

(defn workload
  "A generator, client, and checker for a list-append test."
  [opts]
  (-> (wr/test {:key-count          100
                :key-dist           :exponential
                ;:key-dist           :uniform
                :max-txn-length     (:max-txn-length opts 4)
                :max-writes-per-key (:max-writes-per-key opts)
                :consistency-models [:strong-snapshot-isolation]
                :cycle-search-timeout 1000})
      (assoc :client (Client. nil nil nil))
      (update :checker (fn [c]
                        (checker/compose
                          {
                            :elle c
                            ;; :divergence (divergence-stats-checker)
                            :wr (wr/checker {
                                  :wfr-keys?           true
                                  :sequential-keys?    true
                                  :anomalies           [:G0 :G1c :G-single :G1a
                                                        :G1b :internal]})
                 ;:timeline (timeline/html)})
                            })))))
