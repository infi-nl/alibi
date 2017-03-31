(ns alibi.domain.queries.entry-screen.activity-graphic-test
  (:require
    [alibi.test-helpers :refer [deftest copy-tests]]
    [alibi.db-tools :as db-tools]
    [clojure.test :refer [is testing]]
    [alibi.domain.query-handler :refer [handle]]
    [clojure.pprint :refer [pprint]]
    [alibi.infra.date-time
     :refer [today plus next-week yesterday ->local-time ->local-date
             unix->datetime tomorrow ->unix ->datetime]]))

(defn activity-graphic
  [& {:keys [from user-id]}]
  (handle :entry-screen/activity-graphic
          :user-id (or user-id (db-tools/get-default-user-id))
          :from (or from (today))))


(deftest empty-result-set
  (is (empty? (activity-graphic)) "activity graphic should be empty"))

(deftest result-count
  (apply db-tools/insert-entries! (repeat 3 {:for-date (today)}))
  (let [result (activity-graphic)]
    (is (= 3 (count result)))))

(deftest only-records-for-requested-user-id
  (let [jack (db-tools/new-user! {:name "jack"})
        nina (db-tools/new-user! {:name "nina"})]
    (db-tools/insert-entries! {:for-date (today)
                               :user-id jack})
    (let [result (activity-graphic :user-id nina)]
      (is (= 0 (count result))
          "only results for user nina should be returned"))))

(deftest only-records-in-week
  (let [yesterday (yesterday (today))
        next-week (next-week (today))
        days-this-week (map #(plus (today) [% :days]) (range 0 7))]

    (apply db-tools/insert-entries!
           (map #(-> {:for-date %
                      :start-time "12:00"})
                (concat [yesterday] days-this-week [next-week])))

    (let [result (->> (activity-graphic :from (today))
                      (map (comp ->local-date unix->datetime :from)))]
      (is (= 7 (count result)) "only results from this week should be returned")
      (is (not (some #{yesterday} result))
          "yesterday shouldn't be in result set")
      (is (not (some #{next-week} result))
          "next-week shouldn't be in result set"))))

(defn has-expected? [expected result-set]
  (is (some #(= expected (select-keys % (keys expected))) result-set)
      (str expected " should be in\n"
           (with-out-str (pprint result-set)))))

(deftest result-records-fields
  (testing "task"
    (let [infi-project-id (db-tools/get-project-id "infi")
          xoopd-project-id (db-tools/get-project-id "xoopd")
          other-task-id (db-tools/get-task-id "infi" "overig")
          fail-faster-task-id (db-tools/get-task-id "xoopd" "fail-faster")

          [id1 id2] (db-tools/insert-entries! {:task-id other-task-id}
                                              {:task-id fail-faster-task-id})
          all-expected [{:task-id other-task-id :task "overig"
                         :project-id infi-project-id :project "infi"
                         :entry-id id1}
                        {:task-id fail-faster-task-id :task "fail-faster"
                         :project-id xoopd-project-id :project "xoopd"
                         :entry-id id2}]
          result (activity-graphic)]


      (doseq [expected all-expected]
        (has-expected? expected result))))

  (testing "time"
    (let [entry-id (db-tools/insert-entry! {:for-date (tomorrow)
                                            :start-time "11:00"
                                            :end-time "13:00"})
          result (activity-graphic :from (tomorrow))]
      (has-expected? {:entry-id entry-id
                      :from (->unix (->datetime (tomorrow) "11:00"))
                      :till (->unix (->datetime (tomorrow) "13:00"))
                      :duration 7200} result)))

  (testing "comment"
    (let [entry-id (db-tools/insert-entry!
                     {:comment "This is the longest day of my life"})
          result (activity-graphic)]
      (has-expected? {:entry-id entry-id
                      :comment "This is the longest day of my life"}
                     result)))

  (testing "billable"
    (let [[id1 id2] (db-tools/insert-entries! {:billable? true}
                                              {:billable? false})
          result (activity-graphic)]
      (has-expected? {:entry-id id1 :billable? true} result)
      (has-expected?  {:entry-id id2 :billable? false} result))))


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
