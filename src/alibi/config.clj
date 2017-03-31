(ns alibi.config
  (:require [clojure.edn]
            [clojure.java.io]))

(def config-file (clojure.java.io/resource "config.edn"))

(def ^:private sqlite-env (System/getenv "SQLITE_DBFILE"))

(defn read-config
  ([] (read-config config-file))
  ([f]
   (let [config (some-> f slurp clojure.edn/read-string)]
     (cond-> config
       (:sqlite config) (update-in [:sqlite :subname]
                                   #(or sqlite-env %))))))

(def config (read-config config-file))
