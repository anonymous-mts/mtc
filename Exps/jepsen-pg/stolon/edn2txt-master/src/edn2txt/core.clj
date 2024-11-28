(ns edn2txt.core
  (:gen-class))

(require '[clojure.edn :as edn]
         '[clojure.string :as string]
         '[clojure.java.io :as io])

(defn read-edn-history
  "read history (list of event objects) from edn file"
  [edn-file]
  (->> edn-file
       slurp
       clojure.string/split-lines
       (map edn/read-string)))

(defn append-to-file [file-path content]
  (with-open [writer (io/writer file-path :append true)]
    (binding [*out* writer]
      (println content))))

(defn convert-and-write
  [ok-history file-path]
  ;; (println ok-history)
  (doseq [[index history-entry] (map-indexed vector ok-history)]
    (let [process (:process history-entry)
          events (:value history-entry)]
                    ;;  (println process events)
      (doseq [str (map
                   (fn [event-entry]
                     (let [[type key value] event-entry
                           event-string (str (case type :r "r" :w "w") "(" key "," (if (nil? value) 0 value) "," process "," index ")")]
                       event-string))
                   events)]

        (append-to-file file-path str)))))

(defn -main [& args] ; & creates a list of var-args
  (if (seq args)
    ; Foreach arg, print the arg...
    (doseq [[ednfile outputfile] (partition 2 2 args)]
      (let [history (read-edn-history ednfile)
            ok-history (filter #(= :ok (:type %1)) history)
            result (convert-and-write ok-history outputfile)]))

    ; Handle failure however here
    (throw (Exception. "Must have at least 2 argument!"))))