(ns alibi.actions
  (:require
     ;TODO remove dependency on ag here
    [alibi.activity-graphic-data-source :as ag-ds]))

(def LocalDate (. js/JSJoda -LocalDate))
(def DayOfWeek (. js/JSJoda -DayOfWeek))

(defn find-first-monday [for-date]
  (if (string? for-date) (recur (. LocalDate parse for-date))
    (if (= (. for-date dayOfWeek) (.-MONDAY DayOfWeek))
      for-date
      (recur (. for-date plusDays -1)))))

(defn change-entry-page-date [new-date]
  (println "doing it")
  (fn [dispatch!]
    (let [for-date (if (string? new-date) new-date (.toString new-date))]
      (.then
        (ag-ds/get-data (.toString (find-first-monday for-date)))
        (fn [data]
          (println "done  it")
          (dispatch! {:action :receive-activity-graphic-data
                      :for-date (.toString for-date)
                      :data data}))))))
