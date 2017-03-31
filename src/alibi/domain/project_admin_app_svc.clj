(ns alibi.domain.project-admin-app-svc
  (:require
    [alibi.domain.project.project :as project]
    [alibi.domain.project.repository :as project-repo]))

(defn new-project! [cmd]
  (let [project (project/new-project cmd)]
    (project-repo/add! project)))
