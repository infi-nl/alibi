(ns alibi.datasource.sqlite.app-svc-test
  (:require
    [alibi.datasource.sqlite.fixtures :refer [sqlite-fixture]]
    [clojure.test :refer [test-vars deftest is]]
    [alibi.domain.entry-app-svc-test :as entry]
    [alibi.domain.project-admin-app-svc-test :as project-admin]
    [alibi.domain.task-admin-app-svc-test :as task-admin]
    [alibi.domain.task-repository-test :as task-repo]
    [alibi.config :refer [config]]
    [alibi.db-tools :as db-tools]
    [alibi.domain.queries.entry-screen.entries-for-day-test]
    [alibi.domain.queries.entry-screen.list-all-bookable-projects-and-tasks-test]
    [alibi.domain.queries.entry-screen.activity-graphic-test]
    ))

(task-admin/deftests sqlite-fixture)
(task-repo/deftests sqlite-fixture)
(entry/deftests sqlite-fixture)
(project-admin/deftests sqlite-fixture)

(alibi.domain.queries.entry-screen.entries-for-day-test/deftests sqlite-fixture)
(alibi.domain.queries.entry-screen.list-all-bookable-projects-and-tasks-test/deftests sqlite-fixture)
(alibi.domain.queries.entry-screen.activity-graphic-test/deftests sqlite-fixture)

(comment
  (test-vars [#'get-task]))
