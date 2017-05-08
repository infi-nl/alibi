(ns alibi.datasource.sqlite.entry-repo
  (:require
    [alibi.domain.entry :as entry]
    [clojure.java.jdbc :as db]
    [alibi.infra.date-time
     :refer [format-time ->local-time ->local-date]]
    [alibi.datasource.sqlite.sqlite-helpers :refer [insert-id]]))

(defn- entry->row [entry]
  (let [result
        {:for_date (:for-date entry)
         :start_time (-> (:start-time entry) format-time)
         :end_time (-> (:end-time entry) format-time)
         :user_id (:user-id entry)
         :comment (:comment entry)
         :is_billable (:billable? entry)
         :is_billed (:billed? entry)
         :task_id (:task-id entry)}]
  (cond-> result
    (:entry-id entry) (assoc :id (:entry-id entry)))))

(defn- row->hydratable [row]
  (let [hydratable
        {:for-date (->local-date (:for_date row))
         :task-id (:task_id row)
         :start-time (-> row :start_time ->local-time)
         :end-time (-> row :end_time ->local-time)
         :billable? (pos? (:is_billable row))
         :billed? (pos? (:is_billed row))
         :comment (:comment row)
         :user-id (:user_id row)}]
    (cond-> hydratable
      (:id row) (assoc :entry-id (:id row)))))

(defn add-entry! [db-spec entry]
  (-> (db/insert! db-spec :entries (entry->row entry))
      (insert-id)))

(defn find-entry [db-spec entry-id]
  (let [result-row (db/get-by-id db-spec :entries entry-id)]
    (when result-row
      (entry/hydrate-entry (row->hydratable result-row)))))

(defn save-entry! [db-spec entry]
  ;TODO update only affected columns (maybe put original on meta)
  ;TODO test if there is a test in app svces that assert entry-id is present
  (db/update! db-spec :entries (entry->row entry)
              ["id=?" (:entry-id entry)]))

(defn delete-entry! [db-spec entry-id]
  (db/delete! db-spec :entries ["id=?" entry-id]))

(defn new [db-spec]
  (reify
    entry/EntryRepository
    (-add-entry! [this entry] (add-entry! db-spec entry))
    (-find-entry [this entry-id] (find-entry db-spec entry-id))
    (-save-entry! [this entry] (save-entry! db-spec entry))
    (-delete-entry! [this entry-id] (delete-entry! db-spec entry-id))))
