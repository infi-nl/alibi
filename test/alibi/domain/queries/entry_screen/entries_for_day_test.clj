(ns alibi.domain.queries.entry-screen.entries-for-day-test
  (:require [alibi.domain.query-handler :refer [handle]]
            [alibi.db-tools :as db-tools]
            [clojure.test :refer [is]]
            [alibi.config :refer [config]]
            [alibi.test-helpers :refer [is-equal]]
            [alibi.infra.date-time :refer [today yesterday]]
            [clojure.set :refer [rename-keys]]
            [alibi.test-helpers :refer [deftest copy-tests]]))

(defn entries-for-day
  [& [datum]]
  (-> (handle :entry-screen/entries-for-day
              :day (or datum (today))
              :user-id (db-tools/get-default-user-id))
      :entries))

(defn get-overview
  [& {:keys [for-day for-user]
      :or {for-day (today)
           for-user (db-tools/get-default-user-id)}}]
  (handle :entry-screen/entries-for-day :day for-day :user-id for-user))

(defn get-aggregates-for-day [& [datum]]
  (-> (handle :entry-screen/entries-for-day
              :day (or datum (today))
              :user-id (db-tools/get-default-user-id))
      :aggregates))

(deftest day-entries-no-entries
  (is (= [] (entries-for-day)) "entries not found or not empty"))

(deftest day-entries-field-values
  (do (db-tools/insert-entries!
        {:start-time "13:00" :end-time "14:15"
         :project "infi" :task "overig" :billable? false
         :comment "work work work"})
      (let [entries (entries-for-day)]
        (is (= entries
               [{:start-time "13:00" :end-time "14:15" :duration "1:15"
                 :project "infi" :task "overig" :billable? false
                 :comment "work work work"}])))))

(deftest day-entries-sort-order
  (do (db-tools/insert-entries! {:start-time "13:00" :project "xoopd"}
                                {:start-time "12:00" :project "infi"})
      (let [[first-record second-record] (entries-for-day)]
        (is (:project first-record) "infi must be the first entry")
        (is (:project second-record) "xoopd must be the second entry"))))

(deftest day-entries-billability
  (do (db-tools/insert-entries! {:start-time "12:00" :billable? true}
                                {:start-time "13:00" :billable? false})
      (let [[first-record second-record] (entries-for-day)]
        (is (:billable? first-record)
            "first entry should be billable")
        (is (not (:billable? second-record))
            "second entry should not be billable"))))

(deftest day-entries-date-filter
  (let [today (today)
        yesterday (yesterday)]
    (do (db-tools/insert-entries! {:project "xoopd" :for-date yesterday}
                                  {:project "infi" :for-date today})
        (let [entries-today (entries-for-day today)]
          (is (= 1 (count entries-today)) "1 record expected for today")
          (is (= (-> entries-today first :project) "infi")
              "project infi expected for today"))
        (let [entries-yesterday (entries-for-day yesterday)]
          (is (= 1 (count entries-yesterday)) "1 record expected for yesterday")
          (is (= (-> entries-yesterday first :project) "infi")
              "project infi expected for yesterday")))))

(defn make-user! []
  (db-tools/new-user! {:name "freek"}))

(defn insert-entries! [& entries]
  (doseq [entry entries]
    (db-tools/insert-entries! (rename-keys entry {:for-user :user-id}))))

(defn is-overview-count
  [{:keys [entries]}
   & {:keys [msg count] :or {msg "expected different result count"}}]
  {:pre [count]}
  (is (= count (clojure.core/count entries)) msg))

(defn is-overview-entry-field
  [{:keys [entries]} entry-index field-name field-value
   & {:keys [msg] :or {msg "field value not correct"}}]
  (let [entry (get entries entry-index)]
    (is (= field-value (field-name entry)) msg)))

(deftest day-entries-user-filter
  (let [user-1 (make-user!)
        user-2 (make-user!)]
    (insert-entries!
      {:for-user user-1 :start-time "12:00"}
      {:for-user user-2 :start-time "14:00"})
    (doto (get-overview :for-user user-1)
      (is-overview-count :count 1
                         :msg "overview should only have 1 entry")
      (is-overview-entry-field 0 :start-time "12:00"
                               :msg "time for user-1 is wrong"))))

(deftest day-entries-aggregates
  (do (db-tools/insert-entries! {:start-time "12:00" :end-time "14:30"}
                                {:start-time "14:45" :end-time "16:00"})
      (let [{:keys [total-duration]} (get-aggregates-for-day)]
        (is (= "3:45" total-duration)))))

(defn fixture [f]
  (db-tools/clean-all!)
  (db-tools/new-user! {:name "freak"})
  (db-tools/insert-projects! [{:name "infi"
                               :with-tasks [{:name "overig"}]}
                              {:name "xoopd"
                               :with-tasks [{:name "fail-faster"}]}])
  (f))

(def ^:private my-ns *ns*)

(defmacro deftests [with-fixture]
  (copy-tests my-ns `(fn [the-test#] (~with-fixture
                                       (fn [] (fixture the-test#))))))
