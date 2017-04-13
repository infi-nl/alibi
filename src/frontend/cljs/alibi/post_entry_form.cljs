(ns alibi.post-entry-form
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [clojure.string :as string]
    [alibi.logging :refer [log log-cljs]]
    [time.core :refer [expand-time]]
    [om.core :as om]
    [om.dom :as dom]
    [alibi.actions :as actions]
    [alibi.entry-page-state :as state
     :refer [validate-input-entry validate-form]]))

(defn parse-float [v] (js/parseFloat v))

(defn active-errors
  [input-entry]
  (let [{:keys [formWasSubmitted]} input-entry]
    (seq (when formWasSubmitted
           (validate-input-entry input-entry)))))

(defn summary-errors
  [input-entry]
  (let [{:keys [formWasSubmitted submit-time-state]} input-entry]
    (seq (when formWasSubmitted
           (validate-input-entry submit-time-state)))))

(defn on-form-submit [form on-error event]
  (log "submit")
  (let [errors (validate-form form)]
    (when (seq errors)
      (do
        (.preventDefault event)
        (on-error (state/form->input-entry form))))))

(defn datepicker [data owner]
  (let [get-elements
        (fn []
          (let [el (om/get-node owner "element")
                $el (js/$ el)]
            {:$input-element (.find $el ".datepicker-input")
             :$show-btn (.find $el ".datepicker-show-btn")}))]
    (reify
      om/IDidMount
      (did-mount [_]
        (let [{:keys [$input-element $show-btn]} (get-elements)]
          (. $input-element datepicker #js {:todayBtn "linked",
                                            :todayHighlight true,
                                            :language "nl",
                                            :format "yyyy-mm-dd",
                                            :autoclose true })
          (.. $input-element datepicker
              (on "changeDate"
                  (fn [event]
                    (let [instant (js/JSJoda.Instant.ofEpochMilli
                                    (.. event -date getTime))

                          new-date
                          (.toString (js/JSJoda.LocalDate.ofInstant instant))]
                      ((:onChangeDate data identity) new-date)))))
          (. $show-btn on "click" #(.datepicker $input-element "show"))))

      om/IWillUnmount
      (will-unmount [_]
        (let [{:keys [$input-element $show-btn]} (get-elements)]
          (. $input-element datepicker "destroy")
          (. $show-btn off)))

      om/IRender
      (render [_]
        (dom/div
          #js {:className "input-group input-group-datepicker"
               :ref "element"}
          (dom/input
            #js {:className "form-control datepicker-input"
                 :type "text"
                 :value (:selected-date data)
                 :name (:input-name data)
                 :readOnly true})
          (dom/span
            #js {:className "input-group-addon datepicker-show-btn"}
            (dom/i
              #js {:className "glyphicon glyphicon-calendar"})))))))

(defn validation-summary
  [input-entry]
  (let [summary-errors (summary-errors input-entry)]
    (dom/div
      #js {:className
           (str "alert entry-form-errors"
                (if (active-errors input-entry) " alert-danger" " alert-success")
                (when summary-errors " entry-form-errors-has-errors"))}
      "There is a problem with one or more fields in the form, please correct them:"
      (dom/ul
        #js {:className "entry-form-errors-list-container"}
        (map #(-> (dom/li #js {:key (get % 0)}
                          (dom/strong nil (get % 0))
                          (str " "(get % 1))))
             summary-errors)))))

(defn time-input [id value on-change]
  (dom/input
    #js {:id id :className "form-control time-entry-input" :type "text"
         :name id :value value :size "5" :maxLength "5" :autoComplete "off"
         :placeholder "Time"
         :onFocus #(.. % -target select)
         :onChange #(on-change (.. % -target -value))
         :onBlur #(on-change (expand-time (.. % -target -value)))
         :onKeyDown #(when (= (.-keyCode %) 13)
                       (on-change (expand-time (.. % -target -value))))}))

(defn date-time-entry-row [dispatch! input-entry]
  (let [{:keys [startTime endTime selected-date entry-id]} input-entry
        failed-fields (into {} (active-errors input-entry))
        start-time-error? (failed-fields "Start time")
        end-time-error? (failed-fields "End time")
        on-change-start-time (fn [start-time]
                               (dispatch! {:action :change-start-time
                                           :start-time start-time}))
        on-change-end-time (fn [end-time]
                             (dispatch! {:action :change-end-time
                                         :end-time end-time}))
        time-errors? (or start-time-error? end-time-error?)]
    (dom/div
      #js {:className "form-group"}
      (dom/label
        #js {:className "control-label col-md-2"}
        "Date:")
      (dom/div
        #js {:className "col-md-10"}
        (dom/div
          #js {:className "form-inline form-inline-no-horz-margin"}
          (dom/div
            #js {:className "form-group"}
            (om/build datepicker
                      {:input-name "for-date"
                       :selected-date selected-date
                       :onChangeDate #(dispatch!
                                        (actions/change-entry-page-date %))}))
          " "
          (dom/div
            #js {:className (str "form-group"
                                 (when start-time-error?  " has-error"))}
            (dom/label #js {:htmlFor "start-time"} "From:")
            " "
            (time-input "start-time" startTime on-change-start-time))
          " "
          (dom/div
            #js {:className (str "form-group"
                                 (when end-time-error? " has-error"))}
            (dom/label #js {:htmlFor "end-time"} "To:")
            " "
            (time-input "end-time" endTime on-change-end-time))
          " "
          (dom/div #js {:className "form-group help-block"} "Use format 13:37")
          (dom/div
            #js {:className (when time-errors? "has-error")}
            (dom/span
              #js {:className "help-block entry-forms-errors-date-time-errors"}
              (when start-time-error?
                (dom/span nil (str "Start time: " start-time-error?)
                          (dom/br nil)))
              (when end-time-error?
                (dom/span nil (str "End time: " end-time-error?))))))))))

(defn comment-row [dispatch! input-entry]
  (dom/div
    #js {:className "form-group"}
    (dom/label
      #js {:className "col-md-2 control-label"
           :htmlFor "opmerking"}
      "Comment:")
    (dom/div
      #js {:className "col-md-10"}
      (dom/input
        #js {:type "text"
             :name "comment"
             :value (:comment input-entry)
             :onChange #(dispatch! {:action :change-comment
                                    :comment (.. % -target -value)})
             :id "comment"
             :className "form-control"})
      (dom/span
        #js {:className "help-block"}
        "Provide any details that can help the customer understand what you've worked on."))))

(defn billable?-row [dispatch! input-entry]
  (dom/div
    #js {:className "form-group"}
    (dom/div
      #js {:className "col-md-offset-2 col-md-10"}
      (dom/div
        #js {:className "checkbox"}
        nil
        (dom/label
          nil
          (dom/input
            #js {:type "checkbox"
                 :id "billable"
                 :name "billable?"
                 :checked (:isBillable input-entry)
                 :onChange #(dispatch! {:action :change-billable?
                                        :billable? (.. % -target -checked)})})
          "This entry is billable")))))

(defn btn-row [dispatch! input-entry]
  (let [editing? (integer? (parse-float (:entry-id input-entry)))]
    (dom/div
      #js {:className "form-group"}
      (dom/div
        #js {:className "col-md-offset-2 col-md-10 form-actions"}
        (dom/button
          #js {:className "btn btn-success save-hours"
               :type "submit"}
          (if editing? " Save changes" " Post entry"))
        " "
        (if editing?
          (dom/button
            #js {:className "btn btn-danger delete-entry"
                 :name "delete-entry"}
            "Delete entry"))
        " "
        (dom/button
          #js {:className "btn btn-link"
               :name "cancel"
               :onClick #(do
                           (.preventDefault %)
                           (dispatch! {:action :cancel-entry}))}
          "Cancel")))))

(defn render
  [dispatch! form]
  (let [input-entry (state/form->input-entry form)]
    (dom/form
      #js {:method "post"
           :action (str "/entry/" (get-in form [:selected-date :date]))
           :className "form-horizontal entry-form"
           :onSubmit (partial on-form-submit form
                              #(dispatch! {:action :entry-form-show-errors
                                           :for-entry %}))}
      (let [task (:selected-task form)]
        (dom/div
          #js {:className (string/join
                            " "
                            [(when (summary-errors input-entry) "has-errors")
                             (when (seq task) "entry-form-visible")])
               :id "entry-form-container"}
          (dom/input #js {:type "hidden" :name "selected-project-id"
                          :value (:project-id task "")})
          (dom/input #js {:type "hidden" :name "selected-task-id"
                          :value (:task-id task "")})
          (dom/input #js {:type "hidden" :name "entry-id"
                          :value (get-in form [:post-entry-form :entry-id] "")})
          (dom/div
            #js {:className "row"}
            (dom/div
              #js {:className "col-md-12"}
              (validation-summary input-entry)
              (dom/div
                #js {:className "panel panel-default"}
                (dom/div
                  #js {:className "panel-body"}
                  (dom/fieldset
                    nil
                    (date-time-entry-row dispatch! input-entry)
                    (comment-row dispatch! input-entry)
                    (billable?-row dispatch! input-entry)
                    (btn-row dispatch! input-entry)))))))))))

(defn om-component [for-state owner]
  (reify
    om/IRender
    (render [_]
      (let [form (om/observe owner (state/entry-screen-form))]
        (render (:dispatch! for-state) @form)))))
