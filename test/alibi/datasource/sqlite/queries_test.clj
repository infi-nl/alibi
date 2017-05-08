(ns alibi.datasource.sqlite.queries-test
  (:require
    [alibi.domain.query-handler :refer [handle]]
    [alibi.domain.project :as project]
    [clojure.test :refer [deftest is use-fixtures]]
    [alibi.datasource.sqlite.fixtures
     :refer [sqlite-fixture *db* make-project make-task make-entry]]
    [alibi.infra.date-time :refer [->local-date]]
    [alibi.domain.task :as task]
    [alibi.domain.entry :as entry]))

(def ^:dynamic *defaults* nil)

(use-fixtures :each
              (fn [f]
                (binding [*defaults* {:user-id 1}]
                  (sqlite-fixture f))))


(defn insert-project-and-task! []
  (let [project-id (project/add! (make-project))
        task-id (task/add! (make-task {:for-project-id project-id}))]
    [project-id task-id]))

(defn insert-entry! [for-date]
  (let [[_ task-id] (insert-project-and-task!)]
    (entry/add-entry!
      (make-entry {:task-id task-id
                   :for-date (->local-date for-date)
                   :user-id (:user-id *defaults*)}))))


(deftest list-all-bookable-projects-and-tasks
  (do (insert-project-and-task!)
      (is (seq (handle :entry-screen/list-all-bookable-projects-and-tasks))
          "should have a result")))

(deftest entries-for-day
  (insert-entry! "2017-04-05")
  (let [result (handle :entry-screen/entries-for-day
                       :day (->local-date "2017-04-05")
                       :user-id (:user-id *defaults*))]
    (is (seq (:entries result)) "should have result for this date")
    (is (find result :aggregates) "should have aggregates")))

(deftest activity-graphic
  (insert-entry! "2018-04-05")
  (let [result (handle :entry-screen/activity-graphic
                       :from (->local-date "2018-04-05")
                       :user-id (:user-id *defaults*))]
    (assert (seq result) "should have result")))
