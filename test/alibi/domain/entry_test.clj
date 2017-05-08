(ns alibi.domain.entry-test
  (:require
    [clojure.test :refer :all]
    [alibi.infra.date-time
     :refer [->local-time ->local-date today]]
    [alibi.domain.entry.entry :as entry]
    [alibi.domain.project :as project]
    [alibi.domain.task :as task]))

(defn- valid-entry-data [m]
  (merge {:task-id 1
          :start-time (->local-time "12:00")
          :end-time (->local-time "13:00")
          :for-date (today)
          :billable? false}
         m))

(deftest a-new-entry-cannot-have-a-integer-id
  (is (thrown-with-msg?
        AssertionError #"entry-id"
        (entry/new-entry (valid-entry-data {:entry-id 123})
                         :for-task (task/map->Task {})
                         :for-project (project/map->Project {})))
      "a new entry cannot have an id"))

(deftest a-new-entry-can-have-a-nil-id
  (is (entry/new-entry (valid-entry-data {:entry-id nil})
                     :for-task (task/map->Task {})
                     :for-project (project/map->Project {}))))

(deftest a-new-entry-cannot-be-already-billed
  (is (thrown-with-msg?
        AssertionError #"billed\?"
        (entry/new-entry (valid-entry-data {:billed? true})
                       :for-task (task/map->Task {})
                       :for-project (project/map->Project {})))
      "a new entry cannot be already billed"))
