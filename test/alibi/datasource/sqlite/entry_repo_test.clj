(ns alibi.datasource.sqlite.entry-repo-test
  (:require
    [clojure.test :refer [deftest is use-fixtures testing]]
    [clojure.java.jdbc :as db]
    [alibi.domain.entry.repository :as entry-repo]
    [alibi.infra.date-time :refer [->local-date ->local-time]]
    [alibi.datasource.sqlite.fixtures
     :refer [sqlite-fixture *db* last-insert-rowid make-entry]]
    [clojure.data :refer [diff]]))

(defn insert-entry! [db entry]
  (-> (db/insert! *db* :entries entry)
      (first)
      (last-insert-rowid)))

(use-fixtures :each sqlite-fixture)

(deftest add-entry
  (let [entry-id
        (entry-repo/add-entry!
          (make-entry
            {:task-id 1337 :for-date (->local-date "2017-02-03")
             :start-time (->local-time "12:00")
             :end-time (->local-time "13:00")
             :user-id 42 :comment "hello world!"
             :billable? false :billed? false}))

        row (first (db/query *db*
                             ["select * from entries where id=?" entry-id]))

        expected-row {:id entry-id :for_date "2017-02-03" :start_time "12:00"
                      :end_time "13:00" :user_id 42 :comment "hello world!"
                      :task_id 1337 :is_billable 0 :is_billed 0}]
    (is (= expected-row row))))

(deftest find-entry
  (let [entry-id (insert-entry!
                   *db* {:for_date "2017-02-03" :start_time "12:00"
                         :end_time "13:00" :user_id 42 :comment "allo world"
                         :task_id 1337 :is_billable 1 :is_billed 1})

        expected-fields {:for-date (->local-date "2017-02-03")
                         :start-time (->local-time "12:00")
                         :end-time (->local-time "13:00")
                         :user-id 42 :comment "allo world" :task-id 1337
                         :billable? true :billed? true :entry-id entry-id}

        result (entry-repo/find-entry entry-id)]
    (is result (str "result not found for id " entry-id))
    (when result
      (testing "fields present in result"
        (doseq [field (keys expected-fields)]
          (testing field
            (is (find result field) (str "field " field " not found in " result))
            (is (= (field expected-fields) (field result)))))))))

(deftest save-entry
  (let [entry-id (insert-entry!
                   *db* {:for_date "2017-02-03" :start_time "12:00"
                         :end_time "13:00" :user_id 42 :comment "allo world"
                         :task_id 1337 :is_billable 0 :is_billed 0})

        updated-entry (assoc (entry-repo/find-entry entry-id)
                             :for-date (->local-date "2017-03-03")
                             :start-time (->local-time "14:00")
                             :end-time (->local-time "15:00")
                             :comment "goodbye, world" :task-id 7331
                             :billable? true :billed? true)]
    (do
      (entry-repo/save-entry! updated-entry)
      (let [row (first (db/query
                         *db* ["select * from entries where id=?" entry-id]))]
        (is (= {:id entry-id :for_date "2017-03-03" :start_time "14:00"
                :end_time "15:00" :user_id 42 :comment "goodbye, world"
                :task_id 7331 :is_billable 1 :is_billed 1}
               row))))))


(deftest delete-entry
  (let [entry (make-entry)
        entry-id (entry-repo/add-entry! entry)]
    (do
      (entry-repo/delete-entry! entry-id)
      (is (empty? (db/query *db* ["select * from entries where id=?" entry-id]))
          "record should be deleted from db"))))
