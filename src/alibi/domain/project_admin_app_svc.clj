(ns alibi.domain.project-admin-app-svc
  (:require
    [alibi.domain.project :as project]))

(defn new-project! [cmd]
  (let [project (project/new-project cmd)]
    (project/add! project)))
