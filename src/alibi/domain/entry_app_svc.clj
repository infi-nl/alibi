(ns alibi.domain.entry-app-svc
  (:require
    [alibi.domain.task.repository :as task-repo]
    [alibi.domain.project.repository :as project-repo]
    [alibi.domain.entry.repository :as entry-repo]
    [alibi.domain.user.repository :as user-repo]
    [alibi.domain.entry.entry :as entry]
    [alibi.infra.date-time :refer [local-time? before?
                                                local-date?]]))

(defn- valid-task? [task-id]
  (and
    (integer? task-id)
    (task-repo/task-exists? task-id)))

(defn- valid-user? [user-id]
  (and
    (integer? user-id)
    (user-repo/user-exists? user-id)))

(defn post-new-entry!
  [{:keys [task-id user-id] :as cmd}]
  {:pre [(valid-task? task-id)
         (valid-user? user-id)]}
  (let [task (task-repo/get task-id)
        project (project-repo/get (:project-id task))
        entry (entry/new-entry cmd :for-task task :for-project project)]
    (entry-repo/add-entry! entry)))

(defn valid-billable-value-for-task?
  [task-id billable?-value]
  (let [task (task-repo/get task-id)]
    (entry/valid-billable? billable?-value task)))

(defn validation-errs [m validators]
  (reduce (fn [errs [k validator]]
            (let [field-val (get m k)]
              (if (and field-val (not (validator field-val)))
                (conj errs k)
                errs))) [] validators))

(defn update-entry!
  [as-identity
   {:keys [entry-id start-time end-time user-id for-date task-id] :as cmd}]
  {:pre [(empty? (validation-errs cmd {:start-time local-time?
                                       :end-time local-time?
                                       :for-date local-date?
                                       :task-id valid-task?}))]}
  (assert (not user-id) "you can't update the user-id for an hour entry")
  (let [updatable-fields #{:start-time :end-time :for-date :task-id :comment
                           :billable?}
        entry (entry-repo/find-entry entry-id)
        update-field (fn [entry k]
                       (if (find cmd k)
                         (assoc entry k (k cmd))
                         entry))
        entry' (reduce update-field entry updatable-fields)]
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (assert (not (before? (:end-time entry') (:start-time entry')))
            "start time can't come after end time")
    (assert (not (:billed? entry))
            "can not change an entry when it's already billed")
    (assert (valid-billable-value-for-task? (:task-id entry')
                                            (:billable? entry')))
    (let [old-task (task-repo/get (:task-id entry))
          new-task (when task-id (task-repo/get task-id))
          entry' (entry/update-entry entry old-task new-task cmd)]
      (entry-repo/save-entry! entry'))))

(defn delete-entry! [as-identity {:keys [entry-id] :as cmd}]
  {:pre [(integer? entry-id)
         (integer? as-identity)]}
  (let [entry (entry-repo/find-entry entry-id)]
    (assert entry "Entry not found for user")
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (assert (not (:billed? entry)) "Can't delete an already billed entry")
    (entry-repo/delete-entry! entry-id)))
