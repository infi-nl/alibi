(ns alibi.domain.task-admin-app-svc-test
  (:require
    [alibi.domain.task-admin-app-svc :as tasks]
    [alibi.domain.task.repository :as tasks-repo]
    [clojure.test :refer [is testing]]
    [alibi.db-tools :as db-tools]
    [alibi.test-helpers :refer [deftest copy-tests]]))

(def ^:dynamic *defaults* nil)

(deftest new-task-is-persisted
  (let [task-id (tasks/new-task!
                  {:task-name "a task"
                   :for-project-id (:project-id *defaults*)
                   :billing-method :hourly})
        task (tasks-repo/get task-id)]
    (is task "task should be in database")
    (when task
      (is (= "a task" (:name task)))
      (is (= (:project-id *defaults*) (:project-id task)))
      (is (= :hourly (:billing-method task))))))

(deftest new-task-billing-methods
  (let [project-id (db-tools/insert-project! {:name "a project"
                                              :billing-method :fixed-price})]
    (testing "task specific billing methods"
      (doseq [billing-method [:fixed-price :hourly :overhead]]
        (let [task-id (tasks/new-task!
                        {:task-name (str "a " billing-method " task")
                         :for-project-id project-id
                         :billing-method billing-method})
              task (tasks-repo/get task-id)]
          (is (= billing-method (:billing-method task))))))))

(deftest new-task-validations
  (let [cmd {:task-name "a task"
             :for-project-id (:project-id *defaults*)
             :billing-method :fixed-price}]
    (testing "task name validations"
      (is (thrown? AssertionError (tasks/new-task! (dissoc cmd :task-name)))
          "should have a task name")
      (is (thrown? AssertionError (tasks/new-task! (assoc cmd :task-name "")))
          "should have non empty task name")
      (is (thrown? AssertionError (tasks/new-task! (assoc cmd :task-name 123)))
          "should have string task name"))
    (testing "project validations"
      (is (thrown? AssertionError (tasks/new-task!
                                    (dissoc cmd :for-project-id)))
          "should have a project id")
      (is (thrown? AssertionError (tasks/new-task!
                                    (assoc cmd :for-project-id "qwe")))
          "should have integer project id")
      (is (thrown? AssertionError (tasks/new-task!
                                    (assoc cmd :for-project-id 321)))
          "should have existing project id"))
    (testing "billing method validations"
      (is (thrown? AssertionError (tasks/new-task! (dissoc cmd :billing-method)))
          "should have billing method")
      (is (thrown? AssertionError
                   (tasks/new-task!
                     (assoc cmd :billing-method :non-existing-billing-method)))
          "should have valid billing method")
      (is (thrown? AssertionError
                   (tasks/new-task!
                     (assoc cmd :billing-method :inherit)))
          ":inherit is not a valid billing method anymore"))))

(defn fixture [f]
  (db-tools/clean-all!)
  (db-tools/insert-project! {:name "infi"})
  (with-redefs [*defaults* {:project-id (db-tools/get-project-id "infi")}]
    (f)))

(def ^:private my-ns *ns*)

(defmacro deftests [with-fixture]
  (copy-tests my-ns `(fn [the-test#] (~with-fixture
                                       (fn [] (fixture the-test#))))))
