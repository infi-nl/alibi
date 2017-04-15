(ns alibi.entry-page
  (:require
    [alibi.logging :refer [log log-cljs]]
    [alibi.post-new-entry-bar :as post-new-entry-bar]
    [alibi.post-entry-form :as post-entry-form]
    [alibi.activity-graphic :as activity-graphic]
    [alibi.activity-graphic-data-source :as ag-ds]
    [alibi.day-entry-table :as day-entry-table]
    [cljs.reader]
    [om.core :as om]
    [om.dom :as dom]
    [alibi.entry-page-state :as state :refer [entries]]))

(defonce state (atom state/initial-state))

(enable-console-print!)

(defn dispatch!
  [state-atom action]
  (if (fn? action)
    (action (partial dispatch! state-atom) @state-atom)
    (swap! state-atom state/reducer action)))

(defn fetch-ag-data! [for-date]
  (.then
    (ag-ds/get-data (.toString (activity-graphic/find-first-monday for-date)))
    (fn [data]
      ;(log-cljs "received" for-date)
      (dispatch! state {:action :receive-activity-graphic-data
                        :for-date (.toString for-date)
                        :data data}))))

(let [current-state @state]
  (when-not (seq (entries current-state))
    (log "fetching initial ag data")
    (fetch-ag-data! (get-in current-state [:form :selected-date :date]))))

(def component-state {:dispatch! (partial dispatch! state)
                      :get-state (constantly state)})

; if you wonder why we introduce an itermediate IRender here: it seems Om
; ref-cursors only work if there is at least one om/root that binds to the root
; atom, so we do that here even though it is not passed on to om/build
; see also https://github.com/omcljs/om/issues/864
(om/root
  (fn [_ owner]
    (reify
      om/IRender
      (render [_]
        (log "rerendering root state")
        (om/build post-new-entry-bar/entry-bar-form component-state))))
  state
  {:target (js/document.getElementById "post-new-entry-bar-container")})

(om/root
  post-entry-form/om-component
  component-state
  {:target (js/document.getElementById "entry-form-react-container")})

(om/root
  activity-graphic/render-html
  component-state
  {:target (js/document.getElementById "activity-graphic")})

(om/root
  activity-graphic/render-tooltip
  component-state
  {:target (js/document.getElementById "activity-graphic-tooltip-container")})

(defn render-day-entry-table!
  [for-state]
  (let [new-date (get-in for-state [:form :selected-date :date])]
    (day-entry-table/render "day-entry-table" new-date)))

(add-watch
  state :renderer
  (fn [_ _ _ new-state]
    ;(log "new-state %o" new-state)
    (render-day-entry-table! new-state)))

(reset! state @state)
