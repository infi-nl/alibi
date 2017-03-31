(ns alibi.domain.queries.entry-screen.list-all-bookable-projects-and-tasks-test
  (:require
    [alibi.domain.query-handler
     :refer [with-handler handle]]
    [clojure.test :refer [is testing]]
    [alibi.db-tools :as setup]
    [alibi.test-helpers :refer [deftest copy-tests]]))

(defn list-all-bookable-projects-and-tasks []
  (handle :entry-screen/list-all-bookable-projects-and-tasks))

(defn- partial-item-is-match?
  [partial-item full-item]
  (clojure.set/subset? (set partial-item) (set full-item)))

(defn- assert-result-count
  [result-set expected-count & {:keys [msg]}]
  (is (= expected-count (count result-set)) msg)
  result-set)

(defn- assert-has-project-task
  [result-set & {:keys [project-name task-name msg]}]
  (is (some #(and (= (:project-name %) project-name)
                  (= (:task-name %) task-name)) result-set)
      (or msg (str project-name " - " task-name " not in result set")))
  result-set)

(defn- assert-item-at-index
  [result-set index & item]
  (let [partial-item (apply hash-map item)
        found-item (nth (vec result-set) index)]
    (is (partial-item-is-match? partial-item found-item)
        (str partial-item " not a part of " found-item))
    result-set))

(defn- assert-has-item
  [result-set & item]
  (let [item-map (apply hash-map item)]
    (is (some (partial partial-item-is-match? item-map) result-set)
        (str item-map " not found in result set"))
    result-set))

(deftest bookable-projects-tasks-result-set-names
  (setup/clean-all!)
  (setup/insert-project!
    {:name "infi"}
    :with-tasks [{:name "overig"}
                 {:name "meeting"}])
  (setup/insert-project!
    {:name "acme corp"}
    :with-tasks [{:name "programmeren"}
                 {:name "pm"}])
  (-> (list-all-bookable-projects-and-tasks)
      (assert-result-count 4)
      (assert-has-item :project-name "infi" :task-name "overig")
      (assert-has-item :project-name "infi" :task-name "meeting")
      (assert-has-item :project-name "acme corp"
                       :task-name "programmeren")
      (assert-has-item :project-name "acme corp" :task-name "pm")))

(deftest bookable-projects-tasks-result-set-ids
  (setup/clean-all!)
  (setup/insert-project!
    {:name "infi"}
    :with-tasks [{:name "overig"}])
  (setup/insert-project!
    {:name "acme corp"}
    :with-tasks [{:name "programming"}])
  (-> (list-all-bookable-projects-and-tasks)
      (assert-has-item :project-id (setup/get-project-id "infi")
                       :task-id (setup/get-task-id "infi" "overig"))
      (assert-has-item :project-id (setup/get-project-id "acme corp")
                       :task-id (setup/get-task-id "acme corp"
                                                   "programming"))))

(deftest bookable-projects-tasks-billing-method
  (setup/insert-project!
    {:name "infi" :billing-method :hourly}
    :with-tasks [{:name "overig" :billing-method :fixed-price}
                 {:name "council" :billing-method :overhead}
                 {:name "urenteller" :billing-method :hourly}])
  (-> (list-all-bookable-projects-and-tasks)
      (assert-has-item :project-name "infi"
                       :task-name "overig"
                       :billing-method "Fixed-price")
      (assert-has-item :project-name "infi"
                       :task-name "council"
                       :billing-method "Overhead")
      (assert-has-item :project-name "infi"
                       :task-name "urenteller"
                       :billing-method "Hourly")))

(deftest bookable-projects-tasks-sorting
  (setup/clean-all!)
  (setup/insert-project!
    {:name "infi"}
    :with-tasks [{:name "programming"}
                 {:name "administration"}])
  (setup/insert-project!
    {:name "acme corp"}
    :with-tasks [{:name "testing"}])
  (-> (list-all-bookable-projects-and-tasks)
      (assert-result-count 3)
      (assert-item-at-index 0 :project-name "acme corp" :task-name "testing")
      (assert-item-at-index 1 :project-name "infi" :task-name "administration")
      (assert-item-at-index 2 :project-name "infi" :task-name "programming")))

(def ^:private my-ns *ns*)

(defn fixture [f]
  (setup/clean-all!)
  (setup/new-user! {:name "freak"})
  (setup/insert-projects! [{:name "een project"}])
  (f))

(defmacro deftests [with-fixture]
  (copy-tests my-ns `(fn [the-test#] (~with-fixture
                                       (fn [] (fixture the-test#))))))
