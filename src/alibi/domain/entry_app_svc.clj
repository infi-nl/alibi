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

(defn- user-exists? [user-id]
  {:pre [user-id]}
  (user-repo/user-exists? user-id))

(defn post-new-entry!
  [{:keys [task-id user-id] :as cmd}]
  {:pre [(valid-task? task-id)
         (and (integer? user-id) (user-exists? user-id))]}
  (let [task (task-repo/get task-id)
        project (project-repo/get (:project-id task))
        entry (entry/new-entry cmd :for-task task :for-project project)]
    (entry-repo/add-entry! entry)))

(defn valid-billable-value-for-task?
  [task-id is-billable-value]
  (let [task (task-repo/get task-id)]
    (entry/valid-billable? is-billable-value task)))

(defn update-entry!
  [as-identity
   {:keys [entry-id start-time end-time user-id for-date task-id] :as cmd}]
  {:pre [(or (not start-time) (local-time? start-time))
         (or (not end-time) (local-time? end-time))
         (or (not for-date) (local-date? for-date))
         (or (not task-id) (and (integer? task-id)
                                (valid-task? task-id)))]}
  (assert (not user-id) "you can't update the user-id for an hour entry")
  (let [update-field (fn [entry-key cmd-key entry]
                       (if (find cmd cmd-key)
                         (assoc entry entry-key (cmd-key cmd)) entry))
        entry (entry-repo/find-entry entry-id)
        entry' (->> entry
                 (update-field :start-time :start-time)
                 (update-field :end-time :end-time)
                 (update-field :for-date :for-date)
                 (update-field :task-id :task-id)
                 (update-field :comment :comment)
                 (update-field :billable? :billable?))]
    (assert (not (:billed? entry))
            "can not change an entry when it's already billed")
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (assert (not (before? (:end-time entry') (:start-time entry')))
            "start time can't come after end time")
    (assert (valid-billable-value-for-task? (:task-id entry')
                                            (:billable? entry')))
    (entry-repo/save-entry! entry')))

(defn delete-entry! [as-identity {:keys [entry-id] :as cmd}]
  {:pre [(integer? entry-id)
         (integer? as-identity)]}
  (let [entry (entry-repo/find-entry entry-id)]
    (assert entry "Entry not found for user")
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (assert (not (:billed? entry)) "Can't delete an already billed entry")
    (entry-repo/delete-entry! entry-id)))
