(ns alibi.domain.entry.entry
  (:require
    [alibi.infra.date-time
     :refer [local-time? before? local-date?]]
    [alibi.domain.task.task :refer [task?]]
    [alibi.domain.project.project :refer [project?]]
    [alibi.domain.billing-method :as billing-method]
    [clojure.set :refer [rename-keys]]))

(defn- valid-time-interval? [{:keys [start-time end-time]}]
  (and
    (local-time? start-time)
    (local-time? end-time)
    (before? start-time end-time)))


(defrecord Entry [entry-id
                  task-id
                  for-date
                  start-time
                  end-time
                  user-id
                  comment
                  billable?
                  billed?])

(defn valid-billable?
  [value for-task]
  (if-not value
    true
    (billing-method/billable? (:billing-method for-task))))

(defn- assert-valid-billable?
  [value for-task]
  (assert (valid-billable? value for-task)
          (str "entry can only be billable when task billing method in "
               "#{:hourly :hourly}")))

(defn hydrate-entry
  [{:keys [task-id
           for-date
           start-time
           end-time
           user-id
           billable?
           comment
           billed?] :as cmd}]
  {:pre [(integer? task-id)
         (valid-time-interval? cmd)
         (local-date? for-date)
         (instance? Boolean billable?)
         (or (nil? billed?) (instance? Boolean billed?))]}
  (map->Entry (assoc (rename-keys cmd { })
                     :billed? (boolean billed?))))

(defn new-entry
  [{:keys [billable?] :as cmd}
   & {:keys [for-task for-project]}]
  {:pre [(task? for-task)
         (project? for-project)
         (not (integer? (:entry-id cmd)))
         (not (find cmd :billed?))]}
  (assert-valid-billable? billable? for-task)
  (hydrate-entry cmd))
