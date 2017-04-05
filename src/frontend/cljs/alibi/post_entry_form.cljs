(ns alibi.post-entry-form
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [clojure.string :as string]
    [alibi.logging :refer [log log-cljs]]
    [time.core :refer [expand-time]]))

(declare render-state render-to-dom on-date-change validate-form)

(defn parse-float [v]
  (js/parseFloat v))

(def initial-state
  {:startTime ""
   :endTime ""
   :isBillable false
   :comment ""
   :entry-id "new"
   :formWasSubmitted false
   :submit-time-state nil})

(defn reducer
  [prev-state {:keys [action] :as payload}]
  (case action
    :change-comment
    (assoc prev-state :comment (:comment payload))

    :change-start-time
    (assoc prev-state :startTime (:start-time payload))

    :change-end-time
    (assoc prev-state :endTime (:end-time payload))

    :change-billable?
    (assoc prev-state :isBillable (:billable? payload))

    :cancel-entry initial-state

    :entry-form-show-errors
    (assoc prev-state
           :formWasSubmitted true
           :submit-time-state (:for-entry payload))

    :edit-entry
    (merge initial-state
           (select-keys (:entry payload)
                        [:comment :startTime :endTime :isBillable :entry-id]))

    prev-state))

(defn additional-entry [input-entry]
  (let [input-entry' (-> input-entry
                       (update :startTime expand-time)
                       (update :endTime expand-time))]
    (if-not (seq (validate-form input-entry'))
      input-entry'
      nil)))


(defn c [& args]
  (apply (. js/React -createElement) (map clj->js args)))

(def react-component
  (. js/React createClass
     (clj->js
       {
        :getInitialState
        (fn []
          (this-as this
                   (.-props this)))

        :componentWillReceiveProps
        (fn [props]
          (this-as this
                   (.setState this props)))

        :render
        (fn []
          (this-as
            this
            (let [props-clj (js->clj (.-state this) :keywordize-keys true)]
              (let [result (.call render-state this props-clj)]
                result))))})))

(def time-formatter (.. js/JSJoda -DateTimeFormatter (ofPattern "HH:mm")))

(defn try-parse-time [v]
  (try
    (.. js/JSJoda -LocalTime (from (. time-formatter parse v)))
    (catch js/Error e
      nil)))

(defn validate-form [{:keys [selected-item endTime startTime] :as form-state}]
  (let [validate
        (fn [f field-name msg errs]
          (if (f form-state)
            errs
            (conj errs [field-name msg])))
        errors
        (->> []
             (validate (constantly selected-item) "SelectedItem" "Task not selected")
             (validate #(try-parse-time startTime)
                       "Start time",
                       "Please enter a valid time value (e.g. 13:37)")
             (validate #(try-parse-time endTime)
                       "End time",
                       "Please enter a valid time value (e.g. 13:37)"))
        has-field-error? (into {} errors)]
    (if (or (has-field-error? "Start time") (has-field-error? "End time"))
      errors
      (cond-> errors
        (>= (. (try-parse-time startTime)
              (compareTo (try-parse-time endTime))) 0)
        (conj ["End time" "End time should come after start time"])))))

(defn on-form-submit [entry on-error event]
  (log "submit")
  (let [errors (validate-form entry)]
    (when (seq errors)
      (do
        (.preventDefault event)
        (on-error entry)))))

(def datepicker
  (let [get-elements
        (fn [this]
          (let [el (aget this "element")
                $el (js/$ el)]
            {:$input-element (.find $el ".datepicker-input")
             :$show-btn (.find $el ".datepicker-show-btn")}))]
    (. js/React createClass #js
       {:componentDidMount
        (fn []
          (this-as
            this
            (let [{:keys [$input-element $show-btn]} (get-elements this)]
              (. $input-element datepicker #js {:todayBtn "linked",
                                                :todayHighlight true,
                                                :language "nl",
                                                :format "yyyy-mm-dd",
                                                :autoclose true })
              (.. $input-element datepicker
                  (on "changeDate"
                      #((or (-> (.. this -props)
                                js->clj keywordize-keys
                                :onChangeDate)
                            identity) %)))
              (. $show-btn on "click" #(.datepicker $input-element "show")))))
        :componentWillUnmount
        (fn []
          (this-as
            this
            (let [{:keys [$input-element $show-btn]} (get-elements this)]
              (. $input-element datepicker "destroy")
              (. $show-btn off))))
        :render
        (fn []
          (this-as
            this
            (let [props (.-props this)]
              (html
                [:div.input-group.input-group-datepicker
                 {:ref #(aset this "element" %)}
                 [:input.form-control.datepicker-input
                  {:type "text"
                   :value (aget props "selected-date")
                   :name (aget props "input-name")
                   :read-only true}]
                 [:span.input-group-addon.datepicker-show-btn
                  [:i.glyphicon.glyphicon-calendar]]]))))})))

(defn render-state
  [for-state]
  (let [{:keys [input-entry
                on-change-comment
                on-change-date
                on-change-start-time
                on-change-end-time
                on-change-billable?
                on-cancel-entry
                on-valid-entry
                on-form-submit-error
                ]} for-state
        {:keys [startTime endTime selected-date comment isBillable
                formWasSubmitted submit-time-state selected-item
                entry-id]} input-entry
        {:keys [projectId taskId]} selected-item
        editing? (integer? (parse-float entry-id))]
    (when input-entry
    ;(log "render-state" comment)
      (let [summary-errors (seq (when formWasSubmitted
                                  (validate-form submit-time-state)))
            active-errors (seq (when formWasSubmitted
                                 (validate-form input-entry)))
            failed-fields (into {} active-errors)
            start-time-error? (failed-fields "Start time")
            end-time-error? (failed-fields "End time")
            time-errors? (or start-time-error? end-time-error?)]
        (html
          [:form.form-horizontal.entry-form
           {:method "post"
            :action (str "/entry/" selected-date)
            :onSubmit (partial on-form-submit input-entry on-form-submit-error)}
           [:div#entry-form-container
            {:class (string/join " "
                                 [(when summary-errors "has-errors")
                                  (when selected-item "entry-form-visible")])}
            [:input {:type "hidden"
                     :name "selected-project-id"
                     :value (or projectId "")}]
            [:input {:type "hidden"
                     :name "selected-task-id"
                     :value (or taskId "")}]
            [:input {:type "hidden"
                     :name "entry-id"
                     :value (or entry-id "")}]
            [:div.row
             [:div.col-md-12
              [:div.alert.entry-form-errors
               {:class (str (if active-errors " alert-danger" " alert-success")
                            (when summary-errors " entry-form-errors-has-errors"))}
               "There is a problem with one or more fields in the form, please correct them:"
               [:ul.entry-form-errors-list-container
                (map #(-> [:li {:key (get % 0)}
                           [:strong (get % 0)] (str " "(get % 1))])
                     summary-errors)]]
              [:div.panel.panel-default
               [:div.panel-body
                [:fieldset
                 [:div.form-group
                  [:label.control-label.col-md-2 "Date:"]
                  [:div.col-md-10
                   [:div.form-inline.form-inline-no-horz-margin
                    [:div.form-group
                     ^:inline ;there should be a better way to embed another component
                     (c datepicker {:input-name "for-date"
                                    :selected-date selected-date
                                    :onChangeDate
                                    (fn [event]
                                      (let [instant
                                            (..
                                              js/JSJoda -Instant
                                              (ofEpochMilli
                                                (.. event -date getTime)))

                                            new-date
                                            (.. js/JSJoda -LocalDate
                                                (ofInstant instant) toString)]
                                        (on-change-date new-date)))})]
                    " "
                    [:div.form-group
                     {:class (when start-time-error? "has-error")}
                     [:label {:for "start-time"} "From:"]
                     " "
                     ^:inline
                     (c "input"
                        #js
                      {:id "start-time"
                       :className "form-control time-entry-input"
                       :type "text"
                       :name "start-time"
                       :value startTime
                       :size "5"
                       :maxLength "5"
                       :autoComplete "off"
                       :placeholder "Time"
                       :onFocus #(.. % -target select)
                       :onChange #(on-change-start-time (.. % -target -value))
                       :onBlur #(on-change-start-time (expand-time
                                                        (.. % -target -value)))
                       :onKeyDown #(when (= (.-keyCode %) 13)
                                     (on-change-start-time
                                       (expand-time (.. % -target -value))))
                                      })]
                    " "
                    [:div.form-group
                     {:class (when end-time-error? " has-error")}
                     [:label {:for "end-time"} "To:"]
                     " "
                     ^:inline
                     (c "input"
                      {:type "text"
                       :className "form-control time-entry-input"
                       :id "end-time"
                       :name "end-time"
                       :value endTime
                       :size "5"
                       :maxLength "5"
                       :autoComplete "off"
                       :placeholder "Time"
                       :onFocus #(.. % -target select)
                       :onChange #(on-change-end-time (.. % -target -value))
                       :onBlur #(on-change-end-time (expand-time
                                                      (.. % -target -value)))
                       :onKeyDown #(when (= (.-keyCode %) 13)
                                     (on-change-end-time (expand-time
                                                           (.. % -target -value))))})]
                    " "
                    [:div.form-group.help-block "Use format 13:37"]
                    [:div {:class (when time-errors? "has-error")}
                     [:span.help-block.entry-forms-errors-date-time-errors
                      (when start-time-error?
                        [:span (str "Start time: " start-time-error?) (c "br")])
                      (when end-time-error?
                        [:span (str "End time: " end-time-error?)])]]]]]
                 [:div.form-group
                  [:label.col-md-2.control-label
                   {:for "opmerking"}
                   "Comment:"]
                  [:div.col-md-10
                   ^:inline
                   (c "input"
                      #js {:type "text"
                           :name "comment"
                           :value comment
                           :onChange #(on-change-comment (.. % -target -value))
                           :id "comment"
                           :className "form-control"})
                   [:span.help-block
                    "Provide any details that can help the customer understand what you've worked on."]]]
                 [:div.form-group
                  [:div.col-md-offset-2.col-md-10
                   [:div.checkbox#facturabel-tr
                    [:label
                     ^:inline
                     (c "input"
                        #js
                        {:type "checkbox"
                         :id "billable"
                         :name "billable?"
                         :checked isBillable
                         :onChange #(on-change-billable?
                                      (.. % -target -checked))})
                     "This entry is billable"]]]]
                 [:div.form-group
                  [:div.col-md-offset-2.col-md-10.form-actions
                   [:button.btn.btn-success.save-hours
                    {:type "submit"}
                    (if editing? " Save changes" " Post entry")]
                   " "
                   (if editing?
                     [:button.btn.btn-danger.delete-entry
                      {:name "delete-entry"}
                      "Delete entry"])
                   " "
                   [:button.btn.btn-link
                    {:name "cancel"
                     :onClick #(do
                                 (.preventDefault %)
                                 (on-cancel-entry))}
                    "Cancel"]]]]]]]]]])))))
