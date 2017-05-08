(ns alibi.db-tools
  (:require
    [alibi.domain.task.repository :as task-repo]
    [alibi.domain.task.task :as task]
    [alibi.domain.entry.repository :as entry-repo]
    [alibi.domain.entry.entry :as entry]
    [alibi.infra.date-time
     :refer [today ->local-time]]
    [alibi.domain.project :as project]))

(def ^:dynamic *impl* nil)

(defmacro with-impl [impl & body]
  `(binding [*impl* ~impl]
     ~@body))

(defprotocol DBTools
  (-bill-entry! [this entry-id])
  (-clean-all! [this])
  (-get-default-project-id [this])
  (-get-default-user-id [this])
  (-get-non-existent-user-id [this])
  (-get-project-id [this project-name])
  (-new-user! [this user])
  (-get-task-id [this project-id task-name])
  (-get-default-task-id [this]))

(defn get-default-user-id []
  (-get-default-user-id *impl*))

(defn get-non-existent-user-id []
  Integer/MAX_VALUE)

(defn new-user!
  ([] (new-user! {}))
  ([user-data]
   (-new-user! *impl* user-data)))

(defn get-project-id [project-name]
  (-get-project-id *impl* project-name))

(defn get-default-project-id []
  (-get-default-project-id *impl*))

(defn get-non-existent-task-id []
  Integer/MAX_VALUE)

(defn get-default-task-id []
  (-get-default-task-id *impl*))

(defn bill-entry! [entry-id]
  (-bill-entry! *impl* entry-id))

(defn clean-all! []
  (-clean-all! *impl*))

(defn get-task-id
  ([task-name] (get-task-id nil task-name))
  ([project-id task-name]
  (-get-task-id *impl* project-id task-name)))

(defn- add-project-defaults
  [project]
  (merge {:billing-method :fixed-price}
         project))

(defn insert-task!
  ([task]
   (insert-task! nil task))
  ([project-id task]
   (task-repo/add!
     (task/new-task
       {:for-project-id (or project-id (get-default-project-id))
        :task-name (:name task)
        :billing-method (:billing-method task :hourly)}))))

(defn insert-project! [project & opts]
  (let [project (add-project-defaults project)
        {:keys [with-tasks]} opts

        project-id
        (project/add! (project/new-project
                             {:project-name (:name project)
                              :billing-method (:billing-method project)}))]
    (doseq [task with-tasks]
      (insert-task! project-id task))

    project-id))


(defn insert-projects! [projects]
  (doseq [project projects]
    (insert-project! project :with-tasks (:with-tasks project))))

(defn- entry-task-id
  [impl {:keys [task-id task project-id project] :as entry-data}]
  (or task-id
      (let [project-id (or project-id
                           (if project
                             (-get-project-id impl project)
                             (-get-default-project-id impl)))]
        (-get-task-id impl project-id task))
      (-get-default-task-id impl)))

(defn- prepare-entry [impl {:keys [start-time end-time] :as entry}]
  {:pre [(if (and start-time end-time)
           (<= (compare start-time end-time) 0)
           true)]}
  (let [defaults {:start-time "09:00"
                  :end-time "10:00"
                  :billable? true
                  :comment nil
                  :task-id (entry-task-id impl entry)}]
    (cond
      (not (:user-id entry))
      (recur impl (assoc entry :user-id (-get-default-user-id impl)))

      (or (every? not [start-time end-time])
          (and start-time end-time)) (merge defaults entry)

      start-time (recur impl (assoc entry :end-time "23:59"))
      end-time (recur impl (assoc entry :start-time "00:00")))))


(defn insert-entry! [entry]
  (let [entry (prepare-entry *impl* entry)]
    (entry-repo/add-entry!
      (entry/hydrate-entry {:task-id (:task-id entry)
                            :for-date  (:for-date entry (today))
                            :start-time (->local-time (:start-time entry))
                            :end-time (->local-time (:end-time entry))
                            :user-id (:user-id entry)
                            :billable? (:billable? entry)
                            :comment (:comment entry)
                            :billed? (:billed? entry)}))))

(defn insert-entries! [& entries]
  (doall
    (for [entry entries]
      (insert-entry! entry))))
