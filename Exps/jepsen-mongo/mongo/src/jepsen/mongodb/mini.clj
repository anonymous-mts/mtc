(ns jepsen.mongodb.mini
  "Mini workload"
  (:require [mini.core :as mini]
  ;; (:require [mini.src.mini.core :as mini]
            [jepsen.mongodb.list-append :as la]
            [clojure.tools.logging :refer [info warn]]
            [jepsen.mongodb [client :as c]]
            [jepsen [client :as client]
             [util :as util :refer [timeout]]])
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

  (invoke! [this test wtf]
    (let [txn (:value wtf)]
      (c/with-errors wtf
        (timeout 5000 (assoc wtf :type :info, :error :timeout)
                 (let [db   (c/db conn la/db-name test)
                       txn' (with-open [session (c/start-session conn)]
                              (let [opts (la/txn-options test txn)
                                    body (c/txn
                                                                  ;(info :txn-begins)
                                          (mapv (partial apply-mop!
                                                         test db session)
                                                (:value wtf)))]
                                (.withTransaction session body opts)))
                       _ (info (class wtf))
                       ooo {:index (:index wtf)
                            :time (:time wtf)
                            :process (:process wtf)
                            :f (:f wtf)
                            :type :ok
                            :value txn'}
                       _ (info (class ooo))
                       _ (info ooo)]
                   ooo)))))

  (teardown! [this test])

  (close! [this test]
    (c/close! conn)))


;; (defrecord Client [conn]
;;   client/Client
;;   (open! [this test node]
;;     (assoc this :conn (c/open node test)))

;;   (setup! [this test]
;;     (la/create-coll! test conn))

;;   (invoke! [this test op]
;;     (let [txn (:value op)]
;;       (c/with-errors op
;;         (timeout 5000 (assoc op :type :info, :error :timeoutDbtest2024)
;;           (let [db   (c/db conn la/db-name test)
;;                 txn' (if (and (<= (count txn) 1)
;;                               (not (:singleton-txns test)))
;;                        ; We can run without a transaction
;;                        [(apply-mop! test db nil (first txn))]

;;                        ; We need a transaction
;;                        (with-open [session (c/start-session conn)]
;;                          (let [opts (la/txn-options test txn)
;;                                body (c/txn
;;                                       ;(info :txn-begins)
;;                                       (mapv (partial apply-mop!
;;                                                      test db session)
;;                                             (:value op)))]
;;                            (.withTransaction session body opts))))]
;;             (assoc op :type :ok, :value txn'))))))

;;   (teardown! [this test])

;;   (close! [this test]
;;     (c/close! conn)))

(defn workload
  "a generator, client, and checker for a list-append test."
  [opts]
  (-> (mini/workload opts)
      (assoc :client (Client. nil ))))