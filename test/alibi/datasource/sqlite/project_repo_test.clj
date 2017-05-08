(ns alibi.datasource.sqlite.project-repo-test
  (:require
    [alibi.domain.project :as project]
    [alibi.datasource.sqlite.fixtures
     :refer [*db* sqlite-fixture last-insert-rowid make-project]]
    [clojure.test :refer [deftest is use-fixtures]]
    [clojure.java.jdbc :as db]))

(use-fixtures :each sqlite-fixture)

(defn- insert-row! [row]
  (->
    (db/insert! *db* :projects row)
    first
    last-insert-rowid))

(deftest get-project
  (let [project-id (insert-row! {:id 1337 :name "memphis"
                                 :billing_type "fixed-price" })
        result (project/get project-id)]
    (is result "project not found")
    (when result
      (let [expected-fields {:project-id 1337 :project-name "memphis"
                             :billing-method :fixed-price}]
        (doseq [field (keys expected-fields)]
          (is (find result field) (str "field " field " not found in\n" result))
          (is (= (field expected-fields) (field result))))))))

(deftest add-project
  (let [project (make-project {:billing-method :hourly
                               :project-name "utrecht"})
        project-id (project/add! project)]
    (is project-id "project-id not set")
    (when project-id
      (let [project-row (first
                          (db/query *db* ["select * from projects where id=?"
                                          project-id]))]
        (is (= {:id project-id :billing_type "hourly"
                :name "utrecht"}
               project-row))))))

(deftest add-multiple-projects
  (let [p1 (project/add! (make-project {:project-name "utrecht"}))
        p2 (project/add! (make-project {:project-name "amsterdam"}))]
    (is true "shouldn't throw")))

(deftest project-exists?
  (let [project-id (project/add! (make-project))]
    (is (project/exists? project-id) "project should exist")))
