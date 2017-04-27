(ns alibi.domain.entry-app-svc-test
  (:require
    [clojure.test :refer [testing is use-fixtures]]
    [alibi.domain.entry-app-svc :as svc]
    [alibi.domain.entry.repository :as entry-repo]
    [alibi.db-tools :as db-tools]
    [alibi.infra.date-time :refer [->local-time
                                                ->local-date
                                                today
                                                date-equal?
                                                time-equal?]]
    [clojure.set :refer [rename-keys difference]]
    [alibi.test-helpers :refer [copy-tests deftest]]))

(declare post-ts-entry-and-assert make-valid-command is-invalid-cmd
         is-valid-cmd test-post-billable?-values update-ts-entry-and-assert
         update-ts-entry-and-assert-error make-valid-update-command
         is-valid-update-cmd is-invalid-update-cmd
         test-update-is-billable-values)

(def ^:dynamic *defaults* {})

;start tests regarding posting a new entry

(deftest ts-entry-start-end
  (post-ts-entry-and-assert
    {:start-time "12:00" :end-time "13:00"}
    (fn [{:keys [start-time end-time]}]
      (is (time-equal? "12:00" start-time))
      (is (time-equal? "13:00" end-time)))))

(deftest ts-entry-date
  (post-ts-entry-and-assert
    {:for-date "2016-08-21"}
    (fn [{:keys [for-date]}]
      (is (date-equal? "2016-08-21" for-date)))))

(deftest ts-entry-task-id
  (let [expected-task-id (db-tools/insert-task! {:name "writing time tracker"})]
    (post-ts-entry-and-assert
      {:task-id expected-task-id}
      (fn [{:keys [task-id]}]
        (is (= expected-task-id task-id))))))

(deftest ts-entry-comment
  (post-ts-entry-and-assert
    {:comment "posting timesheet entries"}
    (fn [{:keys [comment]}]
      (is (= "posting timesheet entries" comment)))))

(deftest ts-entry-user-id
  (let [new-user-id (db-tools/new-user!)]
    (post-ts-entry-and-assert
      {:user-id new-user-id}
      (fn [{:keys [user-id]}]
        (is (= new-user-id user-id))))))

(deftest ts-entry-is-billable
  (post-ts-entry-and-assert
    {:billable? true}
    (fn [{:keys [billable?]}]
      (is billable?)))
  (post-ts-entry-and-assert
    {:billable? false}
    (fn [{:keys [billable?]}]
      (is (not billable?)))))

(deftest ts-entry-start-end-validations
  (let [valid-cmd (make-valid-command {})]
    (is-invalid-cmd (dissoc valid-cmd :start-time)
                    ":start-time must be present in command")
    (is-invalid-cmd (assoc valid-cmd :start-time "12:00")
                    ":start-time must be a local time")
    (is-invalid-cmd (dissoc valid-cmd :end-time)
                    ":end-time must be present in command")
    (is-invalid-cmd (assoc valid-cmd :end-time "12:00")
                    ":end-time must be a local time")
    (is-invalid-cmd (-> valid-cmd
                        (assoc :start-time (->local-time "13:00"))
                        (assoc :end-time (->local-time "12:00")))
                    ":start-time can't be after :end-time")
    (is-valid-cmd (-> valid-cmd
                      (assoc :start-time (->local-time "12:00"))
                      (assoc :end-time (->local-time "12:00")))
                  "a duration of 0 should be allowed")))

(deftest ts-entry-date-validations
  (let [valid-cmd (make-valid-command {})]
    (is-invalid-cmd (dissoc valid-cmd :for-date )
                    ":for-date is a required field")
    (is-invalid-cmd (assoc valid-cmd :for-date "2016-08-21")
                    ":for-date should be a local date")))

(deftest ts-entry-task-id-validations
  (let [valid-cmd (make-valid-command {})]
    (is-invalid-cmd (dissoc valid-cmd :task-id)
                    ":task-id is a required field")
    (is-invalid-cmd (assoc valid-cmd :task-id "123")
                    ":task-id should be an integer")
    (is-invalid-cmd (assoc valid-cmd
                           :task-id (db-tools/get-non-existent-task-id))
                    "task should exist")))

(deftest ts-entry-user-id-validations
  (let [valid-cmd (make-valid-command {})]
    (is-invalid-cmd (dissoc valid-cmd :user-id)
                    ":user-id is a required field")
    (is-invalid-cmd (assoc valid-cmd :user-id "123")
                    ":user-id should be an integer")
    (is-invalid-cmd (assoc valid-cmd :user-id
                           (db-tools/get-non-existent-user-id))
                    ":user-id should be an existing user")))

(deftest ts-entry-billable-validations
  (let [valid-cmd (make-valid-command {})]
    (is-invalid-cmd (dissoc valid-cmd :billable?)
                    ":billable? is a required field")

    (is-invalid-cmd (assoc valid-cmd :billable? 1)
                    ":billable? should be boolean")

    (doseq [[billing-method allowed-billable?-values]
            [[:overhead #{false}]
             [:fixed-price #{false}]
             [:hourly #{false true}]]]
      (testing "task billing-method"
        (let [task-name (str (name billing-method) " task")
              task-id (db-tools/insert-task!
                        {:name task-name
                         :billing-method billing-method})
              valid-cmd (assoc valid-cmd :task-id task-id)]
          (test-post-billable?-values
            :testname task-name
            :allowed-billable?-values allowed-billable?-values
            :cmd valid-cmd))))

    (testing "task billing-method has priority over project"
      (let [project-id (db-tools/insert-project!
                         {:name "an hourly billed project"
                          :billing-method :hourly})
            task-id (db-tools/insert-task! project-id
                                          {:name "fixed-price task"
                                           :billing-method :fixed-price})]
        (is-invalid-cmd (assoc valid-cmd
                               :billable? true
                               :task-id task-id)
                        (str "task billing-method=fixed-price,"
                             "shouldn't be able to book entry as billable"))))))
;end tests regarding posting a new entry

;start tests regarding updating an entry

(deftest update-no-changes-doesnt-fail
  (update-ts-entry-and-assert
    :create-cmd {}
    :update-cmd {}
    :assert-fn identity))

(deftest update-start-end-time
  (update-ts-entry-and-assert
    :create-cmd {:start-time "10:00" :end-time "11:00"}
    :update-cmd {:start-time "12:00" :end-time "14:00"}
    :assert-fn (fn [{:keys [start-time end-time]}]
                 (is (time-equal? "12:00" start-time))
                 (is (time-equal? "14:00" end-time)))))

(deftest update-start-end-validations
  (let [valid-cmd (make-valid-update-command {})]
    (is-invalid-update-cmd (assoc valid-cmd :start-time "12:00")
                           "start-time must be a local time")
    (is-invalid-update-cmd (assoc valid-cmd :end-time "12:00")
                           "end-time must be a local time")
    (is-invalid-update-cmd (-> valid-cmd
                        (assoc :start-time (->local-time "13:00"))
                        (assoc :end-time (->local-time "12:00")))
                    "start-time can't be after eind-time")
    (is-invalid-update-cmd (-> valid-cmd
                             (assoc :end-time (->local-time "11:00")))
                    "end-time can't be changed to before original start-time"
                    :with-create-cmd {:start-time "12:00"})))

(deftest update-date
  (update-ts-entry-and-assert
    :create-cmd {:for-date  "2017-05-01"}
    :update-cmd {:for-date "2017-05-02"}
    :assert-fn (fn [{:keys [for-date]}]
                 (is (date-equal? "2017-05-02" for-date)))))

(deftest update-date-validations
  (let [valid-cmd (make-valid-update-command {})]
    (is-invalid-update-cmd (assoc valid-cmd :for-date "2017-08-21")
                           "for-date should be a local date")))


(deftest update-task-id
  (let [original-task-id (db-tools/insert-task! {:name "writing time tracker"})
        new-task-id (db-tools/insert-task! {:name "writing clojure tracker"})]
    (update-ts-entry-and-assert
      :create-cmd {:task-id original-task-id}
      :update-cmd {:task-id new-task-id}
      :assert-fn (fn [{:keys [task-id]}]
                   (is (= new-task-id task-id) "task id not updated")))))

(deftest update-task-id-validations
  (let [valid-cmd (make-valid-update-command {})]
    (is-invalid-update-cmd (assoc valid-cmd :task-id "1337")
                           "task-id should be an integer")
    (is-invalid-update-cmd (assoc valid-cmd
                                  :task-id (db-tools/get-non-existent-task-id))
                           "task should exist")))

(deftest update-comment
  (update-ts-entry-and-assert
    :create-cmd {:comment "original comment"}
    :update-cmd {:comment "new comment"}
    :assert-fn (fn [{:keys [comment]}]
                 (is (= "new comment" comment)))))

(deftest update-is-billable
  (update-ts-entry-and-assert
    :create-cmd {:billable? false}
    :update-cmd {:billable? true}
    :assert-fn (fn [{:keys [billable?]}]
                 (is billable? "entry should be updated to billable")))
  (update-ts-entry-and-assert
    :create-cmd {:billable? true}
    :update-cmd {:billable? false}
    :assert-fn (fn [{:keys [billable?]}]
                 (is (not billable?)
                     "entry should be updated to not-billable"))))

(deftest update-is-billable-validations
  (let [entry-id (svc/post-new-entry! (make-valid-command))
        valid-cmd (make-valid-update-command entry-id {})]
    (doseq [[billing-method allowed-billable?-values]
            [[:overhead #{false}]
             [:fixed-price #{false}]
             [:hourly #{false true}]]]
      (testing (str "task bill kind" billing-method)
        (let [task-name (str (name billing-method) " task")
              task-id (db-tools/insert-task!
                        {:name task-name
                         :billing-method billing-method})
              valid-cmd (assoc valid-cmd :task-id task-id)]
          (test-update-is-billable-values
            :test-name task-name
            :allowed-is-billable-values allowed-billable?-values
            :cmd valid-cmd))))
    (testing "task bill method has priority over project's bill method"
      (let [project-id (db-tools/insert-project!
                         {:name "a hourly billed project"
                          :billing-method :hourly})
            task-id (db-tools/insert-task! project-id
                                          {:name "fixed-price task"
                                           :billing-method :fixed-price})]
        (is-invalid-update-cmd
          (assoc valid-cmd :billable? true :task-id task-id)
          (str "task billing-method=fixed-price,"
               "shouldn't be able to book entry as billable")))))

  (testing "can't change the task to an incompatible bill method"
    (let [fixed-price-task-id (db-tools/insert-task!
                                {:name "a fixed-price task"
                                 :billing-method :fixed-price})
          hourly-task-id (db-tools/insert-task!
                                 {:name "an hourly billed task"
                                  :billing-method :hourly})
          entry-id (svc/post-new-entry!
                     (make-valid-command {:task-id hourly-task-id
                                          :billable? true}))]
      (is-invalid-update-cmd
        (make-valid-update-command
          entry-id
          {:task-id fixed-price-task-id})
        "should not be able to switch a billable entry to a fixed-price task"))))

(deftest update-user-id-should-not-be-allowed
  (let [original-user-id (db-tools/new-user!)
        new-user-id (db-tools/new-user!)]
    (update-ts-entry-and-assert-error
      :create-cmd {:user-id original-user-id}
      :update-cmd {:user-id new-user-id}
      :assert-fn (fn [f]
                   (is (thrown-with-msg? AssertionError #"user-id" (f))
                       "trying to update the user-id should throw an exception"
                       )))))

(deftest update-can-only-update-own-hours
  (let [first-user-id (db-tools/new-user!)
        second-user-id (db-tools/new-user!)
        valid-cmd (make-valid-update-command {})]
    (is-invalid-update-cmd
      valid-cmd "Should not be able to update another user's entry"
      :with-create-cmd {:user-id first-user-id}
      :with-identity second-user-id)))

(deftest update-cannot-update-when-already-billed
  (let [entry-id (svc/post-new-entry! (make-valid-command))]
    (db-tools/bill-entry! entry-id)
    (is-invalid-update-cmd
      (make-valid-update-command entry-id {})
      "should not be able to edit entry when it's already billed")))

;end tests regarding updating an entry

;start tests for deleting entry

(defn post-entry-then-delete []
  (let [post-cmd (make-valid-command)
        entry-id (svc/post-new-entry! post-cmd)]
    (svc/delete-entry! {:entry-id entry-id
                        :as-identity (:user-id post-cmd)})
    entry-id))

(deftest deleting-an-entry-makes-it-ungettable
  (let [entry-id (post-entry-then-delete)]
    (is (not (entry-repo/find-entry entry-id))
        "should not be able get entry when deleted from db")))

(deftest deleting-an-entry-identity-should-be-integer
  (let [entry-id (svc/post-new-entry! (make-valid-command))]
    (is (thrown-with-msg?
          AssertionError #"integer.*as-identity"
          (svc/delete-entry! {:entry-id entry-id})))))

(deftest cannot-delete-someone-elses-entry
  (let [kees (db-tools/new-user! {:name "kees"})
        harry (db-tools/new-user! {:name "harry"})
        post-cmd (make-valid-command {:user-id kees})
        entry-id (svc/post-new-entry! post-cmd)]
    (is (thrown? AssertionError
                 (svc/delete-entry! {:entry-id entry-id
                                     :as-identity harry}))
        "Should not be able to delete kees' record as harry")))

(deftest cannot-delete-an-already-billed-entry
  (let [post-cmd (make-valid-command)
        entry-id (svc/post-new-entry! post-cmd)]
    (db-tools/bill-entry! entry-id)
    (is (thrown? AssertionError
                 (svc/delete-entry! {:entry-id entry-id
                                     :as-identity (:user-id post-cmd)}))
        "Should not be able to delete a billed entry")))


(deftest when-deleting-entry-id-should-be-integer
  (let [post-cmd (make-valid-command)
        entry-id (svc/post-new-entry! post-cmd)]
    (is (thrown? AssertionError
                 (svc/delete-entry! {:entry-id "qwe"
                                     :as-identity (:user-id post-cmd)}))
        "should thrown on non-integer entry-id")))

(deftest when-deleting-non-existing-entry-it-should-throw
  (try
    (svc/delete-entry! {:entry-id 123
                        :as-identity (db-tools/get-default-user-id)})
    (is false "deleting a non existing entry should throw")
    (catch Throwable e
      (is (re-find #"Entry not found for user" (.getMessage e))
          "Wrong exception message"))))


;end tests for deleting entry


;start helper data/functions

(defn init-defaults
  []
  (do
    (db-tools/insert-projects! [{:name "a project"}])
    (db-tools/new-user! {:name "freak"})
    {:user-id (db-tools/get-default-user-id)
     :task-id (db-tools/insert-task! {:name "a task"})}))

(defn make-valid-command
  ([] (make-valid-command {}))
  ([cmd]
   (-> cmd
       (update :user-id (fnil identity (:user-id *defaults*)))
       (update :task-id (fnil identity (:task-id *defaults*)))
       (update :start-time (fnil ->local-time "09:00"))
       (update :end-time (fnil ->local-time "11:00"))
       (update :for-date (fnil ->local-date (today)))
       (update :billable? (fnil boolean false)))))

(defn make-valid-update-command
  ([cmd] (dissoc (make-valid-update-command nil {}) :entry-id))
  ([entry-id cmd]
   (cond-> cmd
     (not (find cmd :entry-id)) (assoc :entry-id entry-id)
     (find cmd :start-time) (update :start-time ->local-time)
     (find cmd :end-time) (update :end-time ->local-time)
     (find cmd :for-date) (update :for-date ->local-date))))


(defn is-valid-cmd
  [cmd msg]
  (is (svc/post-new-entry! cmd) msg))

(defn is-invalid-cmd
  [cmd msg]
  (is (thrown? AssertionError
               (svc/post-new-entry! cmd)) msg))

(defn post-ts-entry-and-assert
  [cmd assert-fn]
  (let [cmd (make-valid-command cmd)
        ts-entry-id (svc/post-new-entry! cmd)
        entry (entry-repo/find-entry ts-entry-id)]
    (when assert-fn
      (assert-fn entry))))



(defn valid-update-cmd-prepare-cmd
  [cmd & [create-cmd]]
  (let [create-cmd (make-valid-command (or create-cmd {}))
        entry-id (svc/post-new-entry! create-cmd)
        cmd' (update cmd :entry-id (fnil identity entry-id))]
    cmd'))

(defn is-valid-update-cmd
  [cmd msg & {:keys [with-create-cmd with-identity]}]
  (do (svc/update-entry!
        (assoc
          (valid-update-cmd-prepare-cmd cmd with-create-cmd)
          :as-identity (or with-identity (:user-id *defaults*))))
      (is true msg)))

(defn is-invalid-update-cmd
  [cmd msg & {:keys [with-create-cmd with-identity]}]
  (is (thrown? AssertionError
               (svc/update-entry!
                 (assoc
                   (valid-update-cmd-prepare-cmd cmd with-create-cmd)
                   :as-identity (or with-identity (:user-id *defaults*)))) msg)))

(defn update-ts-entry-and-assert
  [& {:keys [create-cmd update-cmd assert-fn]}]
  (let [ts-entry-id (svc/post-new-entry! (make-valid-command create-cmd))
        entry (entry-repo/find-entry ts-entry-id)]
      (svc/update-entry! (assoc
                           (make-valid-update-command ts-entry-id update-cmd)
                           :as-identity (:user-id *defaults*)))
      (assert-fn (entry-repo/find-entry ts-entry-id))))

(defn update-ts-entry-and-assert-error
  [& {:keys [create-cmd update-cmd assert-fn]}]
  (let [ts-entry-id (svc/post-new-entry! (make-valid-command create-cmd))
        entry (entry-repo/find-entry ts-entry-id)]
    (assert-fn #(svc/update-entry!
                  (assoc
                    (make-valid-update-command ts-entry-id update-cmd)
                    :as-identity (:user-id *defaults*))))))

(defn test-update-is-billable-values
  [& {:keys [test-name allowed-is-billable-values cmd]}]
  (let [disallowed-is-billable-values (difference
                                       #{true false}
                                       allowed-is-billable-values)]
    (doseq [is-billable allowed-is-billable-values]
      (is-valid-update-cmd
        (assoc cmd :billable? is-billable)
        (str test-name " can be booked as is-billable" is-billable)))
    (doseq [is-billable disallowed-is-billable-values]
      (is-invalid-update-cmd
        (assoc cmd :billable? is-billable)
        (str test-name " can't be booked as is-billable=" is-billable)))))

(defn test-post-billable?-values
  [& {:keys [testname allowed-billable?-values cmd]}]
  (let [disallowed-billable?-values (difference
                                       #{true false}
                                       allowed-billable?-values)]
    (doseq [billable?-value allowed-billable?-values]
      (is-valid-cmd
        (assoc cmd :billable? billable?-value)
        (str testname " can be booked as billable=" billable?-value)))
    (doseq [billable?-value disallowed-billable?-values]
      (is-invalid-cmd
        (assoc cmd :billable? billable?-value)
        (str testname " can't be booked as billable?="
             billable?-value)))))

(defn fixture [f]
  (db-tools/clean-all!)
  (binding [*defaults* (init-defaults)]
    (f)))

(def ^:private my-ns *ns*)

(defmacro deftests[with-fixture]
  (copy-tests my-ns `(fn [the-test#] (~with-fixture
                                       (fn [] (fixture the-test#))))))
