(ns alibi.datasource.sqlite.task-repo-test
  (:require
    [alibi.domain.task.repository :as task-repo]
    [alibi.datasource.sqlite.fixtures
     :refer [*db* sqlite-fixture last-insert-rowid make-task]]
    [clojure.test :refer [deftest is use-fixtures testing]]
    [clojure.java.jdbc :as db]))

(use-fixtures :each sqlite-fixture)

(deftest add-task
  (let [task-id (task-repo/add! (make-task {:for-project-id 1337
                                            :task-name "programming"
                                            :billing-method :hourly}))
        task-row (first
                   (db/query *db* ["select * from tasks where id=?" task-id]))]
    (is task-row "task row not found")
    (when task-row
      (is (= {:id task-id
              :project_id 1337
              :name "programming"
              :billing_type "hourly"} task-row)))))

(deftest add-multiple-tasks
  (let [t1 (task-repo/add! (make-task {:task-name "task 1"}))
        t2 (task-repo/add! (make-task {:task-name "task 2"}))]
    (is true "shouldn't fail")))

(deftest task-exists
  (testing "non-existing task"
    (is (not (task-repo/task-exists? 123)) "task shouldn't exist"))
  (testing "existing task"
    (let [task-id (task-repo/add! (make-task))]
      (is (task-repo/task-exists? task-id) "task should exist"))))

(deftest get-task
  (let [task-id  (-> (db/insert! *db* :tasks {:name "pming"
                                              :project_id 1337
                                              :billing_type "fixed-price"})
                     first
                     last-insert-rowid)
        result (task-repo/get task-id)]
    (is result "task not found")
    (when result
      (let [expected-fields {:task-id task-id
                             :billing-method :fixed-price
                             :name "pming"
                             :project-id 1337}]
        (doseq [field (keys expected-fields)]
          (is (find result field)
              (str "field " field " not found in \n" result))
          (is (= (field expected-fields) (field result))))))))

(deftest project-id-for-task-id
  (let [task-id (task-repo/add! (make-task {:for-project-id 137}))]
    (is (= 137 (task-repo/project-id-for-task-id task-id)))))

(comment (clojure.test/test-vars [#'get-task]))
