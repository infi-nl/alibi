(ns alibi.domain.task-repository-test
  (:require
    [clojure.test :refer [is testing]]
    [alibi.db-tools :as setup]
    [alibi.test-helpers :refer [deftest copy-tests]]
    [alibi.domain.task :as task]))


;TODO we probably need more tests here

(defn- get-default-project-id []
  (setup/get-project-id "een project"))

(defn- assert-has-project-name
  [result-set project-name & {:keys [msg]}]
  (is (some #(= (:project-name %) project-name) result-set) msg)
  result-set)

(deftest project-id-for-task-id
  (setup/insert-project!
    {:name "infi"}
    :with-tasks [{:name "testing"}])
  (let [expected-project-id (setup/get-project-id "infi")
        task-id (setup/get-task-id expected-project-id "testing")]
    (is (= expected-project-id (task/project-id-for-task-id task-id))
        "wrong project-id")))

(deftest billing-methods
  (let [project-id (setup/insert-project! {:name "CTU"
                                           :billing-method :fixed-price})]
    (testing "on task billing methods"
      (doseq [billing-method #{:fixed-price :overhead :hourly}]
        (let [task-id (setup/insert-task! project-id
                                          {:name (str billing-method " task")
                                           :billing-method billing-method})
              task (task/get task-id)]
          (is (= billing-method (:billing-method task)) "wrong billing-method"))))))


(defn fixture [f]
  (setup/clean-all!)
  (setup/new-user! {:name "freak"})
  (setup/insert-projects! [{:name "een project"}])
  (f))

(def ^:private my-ns *ns*)

(defmacro deftests [with-fixture]
  (copy-tests my-ns `(fn [the-test#] (~with-fixture
                                       (fn [] (fixture the-test#))))))
