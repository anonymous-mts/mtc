(ns jepsen.stolon.mini
  "Mini Test"
  (:require [clojure.tools.logging :refer [info]]
            [clojure.string :as str]
            [dom-top.core :refer [disorderly with-retry assert+]]
            [jepsen [client :as client]
             [generator :as gen]
             [checker :as checker]
             [util :as util :refer [parse-long]]
             [store :as store]]
            [jepsen.tests.bank :as bank]
            [jepsen.tests.cycle.append :as append]
            [jepsen.stolon [client :as c]]
            [elle.rw-register :as wr]
            [elle.txn :as et]
            [next.jdbc :as j]
            [next.jdbc.result-set :as rs]
            [next.jdbc.sql.builder :as sqlb]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.pprint :as pprint]))

(def default-table-count 3)
(def max-diff 5)
(def counter (atom 1))

(defn table-name
  "Takes an integer and constructs a table name."
  [i]
  (str "txn" i))

(defn table-for
  "What table should we use for the given key?"
  [table-count k]
  (table-name (mod (hash k) table-count)))
(defn insert!
  "Performs an initial insert of a key with initial element e. Catches
  duplicate key exceptions, returning true if succeeded. If the insert fails
  due to a duplicate key, it'll break the rest of the transaction, assuming
  we're in a transaction, so we establish a savepoint before inserting and roll
  back to it on failure."
  [conn test txn? table k e]
  (try
    (info (if txn? "" "not") "in transaction")
    (when txn? (j/execute! conn ["savepoint upsert"]))
    (info :insert (j/execute! conn
                              [(str "insert into " table " (id, sk, val)"
                                    " values (?, ?, ?)")
                               k k e]))
    (when txn? (j/execute! conn ["release savepoint upsert"]))
    true
    (catch org.postgresql.util.PSQLException e
      (if (re-find #"duplicate key value" (.getMessage e))
        (do (info (if txn? "txn") "insert failed: " (.getMessage e))
            (when txn? (j/execute! conn ["rollback to savepoint upsert"]))
            false)
        (throw e)))))
(defn update!
  "Performs an update of a key k, e. Returns true if the update
  succeeded, false otherwise."
  [conn test table k e]
  (let [res (-> conn
                (j/execute-one! [(str "update " table " set val = ?"
                                      " where id = ?") e k]))]
    (info :update res)
    (-> res
        :next.jdbc/update-count
        pos?)))
(defn mop!
  "Executes a transactional micro-op on a connection. Returns the completed
  micro-op."
  [conn test txn? [f k v] values]
  (let [table-count (:table-count test default-table-count)
        table (table-for table-count k)]
    (Thread/sleep (rand-int 10))
    [f k (case f
           :r (let [r (j/execute! conn
                                  [(str "select (val) from " table " where "
                                        ;(if (< (rand) 0.5) "id" "sk")
                                        "id"
                                        " = ? ")
                                   k]
                                  {:builder-fn rs/as-unqualified-lower-maps})
                    _ (print r)]
                (when-let [v (:val (first r))]
                  (if (nil? v) nil (long v))))
           :w
           (let [new-value v]
             (or (update! conn test table k new-value)
                                ; No dice, fall back to an insert
                 (insert! conn test txn? table k new-value)
                                ; OK if THAT failed then we probably raced with another
                                ; insert; let's try updating again.
                 (update! conn test table k new-value)
                                ; And if THAT failed, all bets are off. This happens even
                                ; under SERIALIZABLE, but I don't think it technically
                                ; VIOLATES serializability.
                 (throw+ {:type     ::homebrew-upsert-failed
                          :key      k
                          :element  v}))
             (long new-value)))]))

(defrecord Client [node conn initialized?]
  client/Client
  (open! [this test node]
    (let [c (c/open test node)]
      (assoc this
             :node          node
             :conn          c
             :initialized?  (atom false))))

  (setup! [_ test]
    (dotimes [i (:table-count test default-table-count)]
      ; OK, so first worrying thing: why can this throw duplicate key errors if
      ; it's executed with "if not exists"?
      (with-retry [conn  conn
                   tries 10]
        (j/execute! conn [(str "drop table if exists " (table-name i))])
        (j/execute! conn
                    [(str "create table if not exists " (table-name i)
                          " (id int not null primary key,
                          sk int not null,
                          val int not null)")])
        (catch org.postgresql.util.PSQLException e
          (condp re-find (.getMessage e)
            #"duplicate key value violates unique constraint"
            :dup

            #"An I/O error occurred|connection has been closed"
            (do (when (zero? tries)
                  (throw e))
                (info "Retrying IO error")
                (Thread/sleep 1000)
                (c/close! conn)
                (retry (c/await-open node)
                       (dec tries)))

            (throw e))))
      ; Make sure we start fresh--in case we're using an existing postgres
      ; cluster and the DB automation isn't wiping the state for us.
      (j/execute! conn [(str "delete from " (table-name i))])))

  (invoke! [_ test op]
    ; One-time connection setup
    (when (compare-and-set! initialized? false true)
      (j/execute! conn [(str "set application_name = 'jepsen process "
                             (:process op) "'")])
      (c/set-transaction-isolation! conn (:isolation test)))

    (c/with-errors op
      (let [txn       (:value op)
            _ (info txn)
            use-txn?  (< 0 (count txn))
            txn'      (if use-txn?
                      ;(if true
                        (j/with-transaction [t conn
                                             {:isolation (:isolation test)}]
                          (reverse (:result (reduce (fn [values-and-result txn]
                                                      (let [values (:values values-and-result)
                                                            result (:result values-and-result)
                                                            [f k v] (mop! t test true txn values)
                                                            new-result (conj result [f k v])
                                                            new-values (if (= f :r) (assoc values k v) values)]
                                                        {:values new-values, :result new-result})) {} txn))))
                        (mapv (partial mop! conn test false) txn))
            _ (info txn')]
        (assoc op :type :ok, :value txn'))))

  (teardown! [_ test])

  (close! [this test]
    (c/close! conn)))

(defn real-ops-value-and-keys
  "generate real operations' value, key1, and key2 from a list cotaining :r1 :r2 :w1 :w2"
  [op-list [key1 key2]]
  (let [key-map {:key1 key1, :key2 key2}]
    (let [_ (swap! counter inc)
          _ (swap! counter inc)])
    (merge key-map
           {:value (map
                    (fn [op] (case op
                               :r1 [:r (:key1 key-map) (long 0)]
                               :r2 [:r (:key2 key-map) (long 0)]
                               :w1 [:w (:key1 key-map) @counter]
                               :w2 [:w (:key2 key-map) (+ 1 @counter)]))
                    op-list)})))

(defn real-ops
  "generate real operations from a list containing :r1 :r2 :w1 :w2"
  [op-list key-list]
  (merge
   {:type :invoke,
    :f :txn}
   (real-ops-value-and-keys op-list key-list)))

(defn r
  "a generator of read one key"
  [key-list]
  (real-ops [:r1] key-list))

(defn r1r2
  "a generator of read two keys(which may be the same)"
  [key-list]
  (real-ops [:r1 :r2] key-list))

(defn r1w1
  "Generator of a transfer: a random amount between two randomly selected
  accounts."
  [key-list]
  (real-ops [:r1 :w1] key-list))

(defn r1r2w2
  "a generator of {read key1, read key2, write key2} 
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r1 :r2 :w2] key-list))

(defn r2r1w2
  "a generator of {read key2, read key1, write key2}
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r2 :r1 :w2] key-list))

(defn r1r1w1
  "a generator of {read key1, read key1, write key1}"
  [key-list]
  (real-ops [:r1 :r1 :w1] key-list))

(defn r2w2r1
  "a generator of {read key2, write key2, read key1}
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r2 :w2 :r1] key-list))

(defn r2w2r2
  "a generator of {read key2, write key2, read key2}"
  [key-list]
  (real-ops [:r2 :w2 :r2] key-list))

(defn r1r2w1w2
  "a generator of {read key1, read key2, write key1, write key2}
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r1 :r2 :w1 :w2] key-list))

(defn r1r2w2w1
  "a generator of {read key1, read key2, write key2, write key1}
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r1 :r2 :w2 :w1] key-list))

(defn r1w1r2w2
  "a generator of {read key1, write key1, read key2, write key2}
   whose key1 and key2 may be the same"
  [key-list]
  (real-ops [:r1 :w1 :r2 :w2] key-list))

(defn rand-key
  "Helper for generators. Takes a key distribution (e.g. :uniform or
  :exponential), a key distribution scale, a key distribution base, and a
  vector of active keys. Returns a random active key."
  [key-dist key-dist-base key-dist-scale active-keys]
  (case key-dist
    :uniform     (rand-nth active-keys)
    :exponential (let [ki (-> (rand key-dist-scale)
                              (+ key-dist-base)
                              Math/log
                              (/ (Math/log key-dist-base))
                              (- 1)
                              Math/floor
                              long)]
                   (nth active-keys ki))))

(defn key-dist-scale
  "Takes a key-dist-base and a key count. Computes the scale factor used for
  random number selection used in rand-key."
  [key-dist-base key-count]
  (-> (Math/pow key-dist-base key-count)
      (- 1)
      (* key-dist-base)
      (/ (- key-dist-base 1))))

(defn fresh-key
  "Takes a key and a vector of active keys. Returns the vector with that key
  replaced by a fresh key."
  [^java.util.List active-keys k]
  (let [i (.indexOf active-keys k)
        k' (inc (reduce max active-keys))]
    (assoc active-keys i k')))

(def txn-f-list [r r1r2 r1w1 r1r2w2 r2r1w2 r1r1w1 r2w2r1 r2w2r2 r1r2w1w2 r1r2w2w1 r1w1r2w2])

(defn writes?
  [txn key]
  (let [ops (:value txn)]
    (some (fn [[t k _]]
            (and (= :w t)
                 (= key k))) ops)))

(defn diff-key-txn-and-next-state
  [opts state]
  (let [max-writes-per-key   (:max-writes-per-key  opts 32)
        key-dist             (:key-dist            opts :exponential)
        key-dist-base        (:key-dist-base       opts 2)
        key-count            (:key-count           opts)
                   ; Choosing our random numbers from this range converts them to an
                   ; index in the range [0, key-count).
        key-dist-scale       (key-dist-scale key-dist-base key-count)
        ^java.util.List active-keys (:active-keys state)
        key1 (rand-key key-dist key-dist-base
                       key-dist-scale active-keys)
        key2 (rand-key key-dist key-dist-base
                       key-dist-scale active-keys)
        f (rand-nth txn-f-list)
        v1 (get state key1 1)
        v2 (get state key2 1)]
    (if (= key1 key2)
      (recur opts state)
      (if (< max-writes-per-key v1)
        (let [state' (update state :active-keys
                             fresh-key key1)]
          (recur opts state'))
        (if (< max-writes-per-key v2)
          (let [state' (update state :active-keys
                               fresh-key key2)]
            (recur opts state'))
          (let [txn (f [key1 key2])
                writes-key1? (writes? txn key1)
                state' (if writes-key1?
                         (assoc state key1 (inc v1))
                         state)
                writes-key2? (writes? txn key2)
                state'' (if writes-key2?
                          (assoc state' key2 (inc v2))
                          state')]
            [txn state'']))))))

(defn mini-txns
  "A lazy sequence of write and read transactions over a pool of n numeric
  keys; every write is unique per key. Options:

    :key-dist             Controls probability distribution for keys being
                          selected for a given operation. Choosing :uniform
                          means every key has an equal probability of appearing.
                          :exponential means that key i in the current key pool
                          is k^i times more likely than the first key to be
                          chosen. Defaults to :exponential.

    :key-dist-base        The base for an exponential distribution. Defaults
                          to 2, so the first key is twice as likely as the
                          second, which is twice as likely as the third, etc.

    :key-count            Number of distinct keys at any point. Defaults to
                          10 for exponential, 3 for uniform.

    :min-txn-length       Minimum number of operations per txn

    :max-txn-length       Maximum number of operations per txn

    :max-writes-per-key   Maximum number of operations per key"
  ([opts]
   (let [key-dist  (:key-dist  opts :exponential)
         key-count (:key-count opts (case key-dist
                                      :exponential 10
                                      :uniform     3))]
     (mini-txns (assoc opts
                       :key-dist  key-dist
                       :key-count key-count)
                {:active-keys (vec (range key-count))})))
  ([opts state]
   (lazy-seq
    (let [[txn state] (diff-key-txn-and-next-state opts state)]
      (cons txn (mini-txns opts state))))))

(defn generator
  "A mixture of reads and transfers for clients."
  []
  (gen/mix [r r1r2 r1w1 r1r2w2 r2r1w2 r1r1w1 r2w2r1 r2w2r2 r1r2w1w2 r1r2w2w1 r1w1r2w2]))

(defn checker
  ([]
   (checker {:anomalies [:G1 :G2]}))
  ([opts]
   (reify checker/Checker
     (check [this test history checker-opts]
       (wr/check (assoc opts :directory
                        (.getCanonicalPath
                         (store/path! test (:subdirectory opts) "elle")))
                 history)))))

(defn noob-checker
  []
  (reify checker/Checker
    (check [_ _ _ _]
      {:valid? true})))

(defn workload
  "指定generator和checker"
  [opts]
  (merge nil
         {:generator (mini-txns opts)
          :checker (checker)
          :client (Client. nil nil nil)}))
