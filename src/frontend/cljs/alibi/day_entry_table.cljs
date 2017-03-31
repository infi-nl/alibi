(ns alibi.day-entry-table
  (:require [alibi.logging :refer [log]]))

(defn current-selected-date-el []
  (.getElementById js/document "day-entry-table-for-date"))

(defn render [container-id new-date]
  (let [selected-date (current-selected-date-el)
        container (.getElementById js/document container-id)
        old-date (aget selected-date "value")]
    (when (not= old-date new-date)
      ;(log "old-date" old-date)
      ; we use $selected-date to prevent multiple ajax requests for same date
      (aset selected-date "value" new-date)
      (.. js/$
         (ajax
           #js {:method "get"
                :url (str "/entry/day-entries?for-date=" new-date)})
         (done
           (fn [html] (aset container "innerHTML" html)))
         (fail
           (fn []
             (aset selected-date "value" old-date)))))))
