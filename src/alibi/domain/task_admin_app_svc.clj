(ns alibi.domain.task-admin-app-svc
  (:require
    [alibi.domain.task.repository :as tasks-repo]
    [alibi.domain.task.task :as task]
    [alibi.domain.project.repository :as projects-repo]))

(defn new-task!
  [{:keys [for-project-id] :as cmd}]
  {:pre [(projects-repo/exists? for-project-id)]}
  (let [task (task/new-task cmd)]
    (tasks-repo/add! task)))
