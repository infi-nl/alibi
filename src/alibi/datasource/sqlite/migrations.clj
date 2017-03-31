(ns alibi.datasource.sqlite.migrations
  (:require
    [clojure.java.jdbc :as db]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defn- get-migrations [path]
  (some->> (io/resource path)
           (io/file)
           (.list)
           (filter (partial re-matches #"^[0-9]{4}\..*\.sql.edn$"))
           (sort)))

(defn- sqlite-get-tables [db]
  (map :name
       (db/query db "select name from sqlite_master where type='table'")))

(defn- get-latest-migration [db]
  (when (some #{"db_migrations"} (sqlite-get-tables db))
    (let [migs (db/query db (str "select version,filename from db_migrations"
                                 " order by version desc limit 1"))]
      (assert (seq migs) "db_migrations table exists, but is empty")
      (first migs))))

(defn- apply-migration! [db migrations-path filename]
  (let [res (io/resource (str migrations-path "/" filename))
        stmts (edn/read-string (slurp res))]
    (doseq [stmt stmts]
      (db/execute! db stmt))))

(defn apply-migrations! [db migrations-path]
  (let [latest-migration (get-latest-migration db)]
    (loop [[mig & more] (get-migrations migrations-path)]
      (when mig
        (if (and latest-migration
                 (<= (compare mig (:filename latest-migration)) 0))
          (recur more)
          (do
            (apply-migration! db migrations-path mig)
            (recur more)))))))
