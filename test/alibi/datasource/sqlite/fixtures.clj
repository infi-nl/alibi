(ns alibi.datasource.sqlite.fixtures
  (:require
    [alibi.datasource.sqlite.migrations :as migrations]
    [alibi.domain.project :refer [new-project]]
    [alibi.datasource.sqlite.bootstrap :refer [with-sqlite]]
    [alibi.domain.task.task :refer [new-task]]
    [alibi.domain.entry.entry :as entry]
    [alibi.domain.user.repository :as user-repo]
    [alibi.infra.date-time :refer [->local-date ->local-time]]
    [alibi.db-tools :as db-tools]
    [alibi.datasource.sqlite.db-tools :refer [sqlite-db-tools
                                                           sqlite-user-repo]]))

(def ^:private migrations-path "datasources/sqlite/migrations")

(defn get-db-spec [db-file]
  {:subprotocol "sqlite"
   :subname db-file})

(defn get-temp-db-file []
  (.getAbsolutePath
    (java.io.File/createTempFile "alibi-sqlite-" ".db")))

(defn init-db! [db-spec]
  (migrations/apply-migrations! db-spec migrations-path)
  db-spec)

(def ^:dynamic *db*)

(defn sqlite-fixture [f]
  (let [db-file (get-temp-db-file)
        db (-> (get-db-spec db-file)
               (init-db!))]
    ;(println "using db file" db-file)
    (binding [*db* db]
      (db-tools/with-impl (sqlite-db-tools db)
        (with-sqlite db
          (user-repo/with-impl (sqlite-user-repo)
            (f)))))))

(def last-insert-rowid (keyword "last_insert_rowid()"))

(defn make-project
  ([] (make-project {}))
  ([project]
   (new-project (merge {:billing-method :fixed-price
                        :project-name "a project"}
                       project))))

(defn make-task
  ([] (make-task {}))
  ([task]
   (new-task (merge
               {:for-project-id 1 :task-name "a task"
                :billing-method :fixed-price}
               task))))

(defn make-entry
  ([] (make-entry {}))
  ([fields]
   (entry/hydrate-entry
     (merge {:task-id 123 :for-date (->local-date "2018-02-03")
             :start-time (->local-time "10:00")
             :end-time (->local-time "11:00")
             :user-id 321 :comment ""
             :billable? false :billed? false}
            fields))))
