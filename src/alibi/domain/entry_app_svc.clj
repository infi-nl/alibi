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

(defn update-entry!
  [as-identity cmd]
  (let [task-id (:task-id cmd)
        entry (entry-repo/find-entry (:entry-id cmd))]
    (when task-id
      (assert (valid-task? task-id)))
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (entry-repo/save-entry!
      (entry/update-entry
        entry (assoc cmd
                     :old-task (task-repo/get (:task-id entry))
                     :new-task (when task-id (task-repo/get task-id)))))))

(defn delete-entry! [as-identity {:keys [entry-id] :as cmd}]
  {:pre [(integer? entry-id)
         (integer? as-identity)]}
  (let [entry (entry-repo/find-entry entry-id)]
    (assert entry "Entry not found for user")
    (assert (= as-identity (:user-id entry))
            "can only updates entries for yourself")
    (assert (not (:billed? entry)) "Can't delete an already billed entry")
    (entry-repo/delete-entry! entry-id)))
