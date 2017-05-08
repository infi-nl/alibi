(ns alibi.domain.task-admin-app-svc
  (:require
    [alibi.domain.task :as task]
    [alibi.domain.project :as project]))

(defn new-task!
  [{:keys [for-project-id] :as cmd}]
  {:pre [(project/exists? for-project-id)]}
  (let [task (task/new-task cmd)]
    (task/add! task)))
