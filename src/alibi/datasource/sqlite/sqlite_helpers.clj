(ns alibi.datasource.sqlite.sqlite-helpers)

(def row-id-keyword (keyword "last_insert_rowid()"))

(defn insert-id [insert-result]
  (-> insert-result
      first
      row-id-keyword))
