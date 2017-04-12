(ns alibi.activity-graphic
  (:require
    [clojure.walk :refer [keywordize-keys]]
    [alibi.logging :refer [log log-cljs]]
    [om.core :as om]
    [om.dom :as dom]
    [clojure.string :as string]
    [alibi.entry-page-state :as state]
    [alibi.post-entry-form :as post-entry-form]
    [alibi.actions :as actions]))

(def ZoneId (. js/JSJoda -ZoneId))
(def LocalDate (. js/JSJoda -LocalDate))
(def LocalTime (. js/JSJoda -LocalTime))
(def ChronoUnit (. js/JSJoda -ChronoUnit))
(def Duration (. js/JSJoda -Duration))
(def DayOfWeek (. js/JSJoda -DayOfWeek))
(def Instant (. js/JSJoda -Instant))

(def default-min-time  (. LocalTime parse "08:00"))
(def default-max-time  (. LocalTime parse "18:00"))

(defn parse-float [n] (js/parseFloat n))

(defn get-abs-bounding-client-rect [dom-el]
  (let [rect (.getBoundingClientRect dom-el)
        scroll-x (.-scrollX js/window)
        scroll-y (.-scrollY js/window)]
    {:top (+ (.-top rect) scroll-y)
     :left (+ (.-left rect) scroll-x)
     :right (+ (.-right rect) scroll-x)
     :bottom (+ (.-bottom rect) scroll-y)
     :width (.-width rect)
     :height (.-height rect)}))

(def drawing-contants
  {:pixels-per-minute 0.2
   :pixels-per-day 144
   :left-axis-px 100
   :project-label-offset-px 5
   :project-label-vert-offset-px 4
   :projects {:vertical-offset-px 25}
   :hour-entry {:height-px 8
                :active {:color "rgb(240, 153, 45)",
                         :radius 3
                         :std-dev 2}}
   :row-spacing-px 14
   :day-spacing-px 20
   :project-spacing-px 20
   :project-heading-x-px 10
   :heading-to-rows-px 15
   :grid-time-label {:x-offset-px 5
                     :height-px 15}
   :grid-day-label {:x-offset-px 5
                    :height-px 20}
   :day-summary  {:bar {:y-offset-px  8}
                  :label {:x-offset-px 5
                          :y-offset-px 15}}
   :canvas {:bottom-padding-px 10}
   :no-data {:y-offset-px 50
             :x-offset-px 540
             :bottom-padding-px 50}
   :time-line  {:width-px 8}})

(defn find-first-monday [for-date]
  (if (string? for-date) (recur (. LocalDate parse for-date))
    (if (= (. for-date dayOfWeek) (.-MONDAY DayOfWeek))
      for-date
      (recur (. for-date plusDays -1)))))

(defn c [el attrs & children]
  ;(log "attrs %o" attrs)
  (apply js/React.createElement el attrs children))

(defn svg-glow [id color & {:keys [radius std-dev] :or {:radius 1 :std-dev 1}}]
  ;from http://stackoverflow.com/a/36564885/345910
  (str "<filter id='" id "' x='-5000%' y='-5000%' width='10000%' height='10000%'>
         <feFlood result='flood' flood-color='" color "' flood-opacity='1'></feFlood>
         <feComposite in='flood' result='mask' in2='SourceGraphic' operator='in'></feComposite>
         <feMorphology in='mask' result='dilated' operator='dilate' radius='" radius "'></feMorphology>
         <feGaussianBlur in='dilated' result='blurred' stdDeviation='" std-dev "'></feGaussianBlur>
         <feMerge>
           <feMergeNode in='blurred'></feMergeNode>
           <feMergeNode in='SourceGraphic'></feMergeNode>
         </feMerge>
       </filter>"))

(defn draw-result-empty []
  {:y-offset 0 :els []})

(defn draw-result-append-el [el draw-result]
  (update draw-result :els conj el))

(defn draw-result-add-to-y [inc draw-result]
  (update draw-result :y-offset + inc))

(defn draw-result-set-y-offset [y-offset draw-result]
  (assoc draw-result :y-offset y-offset))

(defn drawing-const [keys & default]
  (get-in drawing-contants keys default))

(defn bars-sum-duration [bars]
  (. Duration ofMinutes
     (reduce (fn [sum {:keys [from till] :as next}]
               (+ sum (. from until till (.-MINUTES ChronoUnit))))
             0 bars)))

(defn bars-merge [bars]
  (letfn [(bars-overlap? [a b]
            (if (< (.compareTo (:from b) (:from a)) 0)
              (recur b a)
              (>= (.compareTo (:till a) (:from b)) 0)))

          (merge-recur [merged [first second & more :as remaining]]
            (cond
              (not first) merged
              (or
                (not second)
                (not (bars-overlap? first second))) (recur (conj merged first)
                                                           (rest remaining))
              :else (recur merged (concat [{:from (:from first)
                                            :till (:till second)}]
                                          more))))]
    (merge-recur [] (sort (fn [{a-from :from} {b-from :from}]
                            (.compareTo a-from b-from))
                          bars))))

(defn date->grid-label [for-date]
  (let [months ["jan" "feb", "mar", "apr", "may", "jun", "jul", "aug",
                    "sep" "oct", "nov", "dec"]
        weekDays  ["Mon" "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]]
    (str (weekDays (dec (.. for-date dayOfWeek value)))
         ", "
         (. for-date dayOfMonth)
         " "
         (months (dec (.. for-date month value)))
         " '"
         (mod (. for-date year) 100))))

(defn init-data [project-data]
  (->> project-data
       (sort (fn [{a-from :from} {b-from :from}] (compare a-from b-from)))
       (map (fn [{:keys [from till task-id] :as project-row}]
              (assoc project-row
                     :task-id (parse-float task-id)
                     :from (. Instant ofEpochSecond from)
                     :till (. Instant ofEpochSecond till))))
       (group-by :project)
       (map (fn [[k vs]]
              {:label k
               :bars (->> vs
                          (sort (fn [{a-from :from}
                                     {b-from :from}]
                                  (.compareTo a-from b-from))))}))))

(defn calc-min-max-time [projects]
  (cond (= 0 (count projects)) [default-min-time
                                default-max-time]
        :else
        [(->> projects
              (mapcat :bars)
              (map #(.ofInstant LocalTime (:from %)))
              (sort (fn [a b] (. a compareTo b)))
              (take 1)
              (map #(. % truncatedTo (.-HOURS ChronoUnit)))
              (first))
         (->> projects
              (mapcat :bars)
              (map #(.ofInstant LocalTime (:till %)))
              (sort (fn [a b] (. b compareTo a)))
              (take 1)
              (map #(. % truncatedTo (.-HOURS ChronoUnit)))
              (map #(.plusHours % 1))
              (first))]))

(defn instant-to-x
  [min-time min-date minutes-per-day pixels-per-minute instant]
  (let [day (. min-date until
               (. LocalDate ofInstant instant)
               (. ChronoUnit -DAYS))
        time (. LocalTime ofInstant instant)]
    (+ (* day minutes-per-day pixels-per-minute)
       (* pixels-per-minute (. min-time until time (. ChronoUnit -MINUTES)))
       (drawing-const [:left-axis-px]))))

(defn iterate-day-grid
  [min-time min-date instant-to-x f init-value]
  (let [days 7]
    (reduce
      (fn [acc day-index]
        (let [get-instant (fn [for-day-index]
                            (.. min-date
                                (plusDays for-day-index)
                                (atTime min-time)
                                (atZone (. ZoneId systemDefault))
                                (toInstant)))
              day-instant (get-instant day-index)
              next-day-instant (get-instant (inc day-index))
              x1 (instant-to-x day-instant)
              x2 (instant-to-x next-day-instant)]
          (f {:left-x x1
              :right-x x2
              :left-instant day-instant
              :right-instant next-day-instant
              :is-first (= day-index 0)
              :is-last (= day-index (dec days))} acc)))
      init-value
      (range 0 days))))

(defn render-grid-labels
  [on-change-date selected-date min-time max-time iterate-day-grid draw-result]
  (->>
    draw-result
    (iterate-day-grid
      (fn [{:keys [left-x left-instant]}
           {:keys [y-offset] :as draw-result}]
        (let [date (.ofInstant LocalDate left-instant)]
          (->> draw-result
               (draw-result-append-el
                 (dom/text
                   #js
                   {:className (str "grid-day-label"
                                    (when (.equals date selected-date)
                                      " grid-day-label-is-selected"))
                    :x (+ left-x
                          (drawing-const
                            [:grid-day-label :x-offset-px]))
                    :y (+ y-offset
                          (drawing-const
                            [:grid-day-label :height-px]))
                    :onClick #(on-change-date date)}
                   (-> (. LocalDate ofInstant left-instant)
                       date->grid-label)))))))
    (draw-result-add-to-y
      (+ (drawing-const [:grid-day-label :height-px])
         (drawing-const [:grid-time-label :height-px])))
    (iterate-day-grid
      (fn [{:keys [left-x right-x]} {:keys [y-offset] :as draw-result}]
        (->> draw-result
             (draw-result-append-el
               (dom/text
                 #js
                 {:className "grid-time-label"
                  :x (+ left-x
                        (drawing-const [:grid-time-label :x-offset-px]))
                  :y y-offset}
                 (.toString min-time)))
             (draw-result-append-el
               (dom/text
                #js
                {:className "grid-time-label"
                 :x (- right-x
                       (drawing-const [:grid-time-label :x-offset-px]))
                 :y y-offset
                 :textAnchor "end"}
                (.toString max-time))))))))

(defn draw-no-data-msg [draw-result]
  (->> draw-result
       (draw-result-append-el
         (dom/text
           #js
           {:className "no-data-in-period"
            :x (drawing-const [:no-data :x-offset-px])
            :y (+ (:y-offset draw-result)
                  (drawing-const [:no-data :y-offset-px]))
            :textAnchor "middle"}
           "No data for this period"))
       (draw-result-add-to-y
         (+ (drawing-const [:no-data :y-offset-px])
            (drawing-const [:no-data :bottom-padding-px])))))

(defn noop [& more] nil)

(defn render-row
  [selected-entry instant-to-x text bars
   {:keys [right-label on-mouse-over-bar on-mouse-leave-bar on-click-bar]
    :or {on-mouse-over-bar noop on-mouse-leave-bar noop}
    :as opts}
   draw-result]
  (let [x-start (-
                 (instant-to-x (->> bars (map :from)
                                    (sort (fn [a b] (. a compareTo b)))
                                    first))
                 (drawing-const [:project-label-offset-px]))

        introduce-gap-between-adjacent-bars
        (fn [result [first second & more :as bars-with-positions]]
          (cond
            (not first) result
            (or (not second)
                (not= (:x2 first)
                      (:x1 second))) (recur (conj result first)
                                            (rest bars-with-positions))
            :else (recur (conj result (update first :x2 dec))
                         (rest bars-with-positions))))

        drawable-bars (->> bars
                           (map #(-> {:x1 (instant-to-x (:from %))
                                      :x2 (instant-to-x (:till %))
                                      :bar %}))
                           (introduce-gap-between-adjacent-bars []))
        draw-bars
        (fn [draw-result]
          (reduce
            (fn [draw-result bar]
              (cond->> draw-result
                (= (get-in bar [:bar :entry-id]) selected-entry)
                (draw-result-append-el
                  (let [height (drawing-const [:hour-entry :height-px])]
                    (dom/rect
                      #js
                      {:className "hour-entry-active"
                       :x (:x1 bar)
                       :width (- (:x2 bar) (:x1 bar))
                       :y (- (:y-offset draw-result) (/ height 2))
                       :height height
                       :style #js {:filter "url(#hour-entry-active-glow)"}})))

                :always
                (draw-result-append-el
                  (dom/line
                   #js
                   {:className (str "time-line hour-line "
                                    (when (-> bar :bar :billable?)
                                      "hour-line-is-billable"))
                    :x1 (:x1 bar)
                    :x2 (:x2 bar)
                    :y1 (:y-offset draw-result)
                    :y2 (:y-offset draw-result)

                    :onMouseOver
                    (fn [ev]
                      (let [rect (get-abs-bounding-client-rect
                                   (.-target ev))]
                        (on-mouse-over-bar
                          {:entry-id (get-in bar [:bar :entry-id])
                           :pos rect})))

                    :onMouseOut #(on-mouse-leave-bar)

                    :onClick
                    (fn [ev]
                      (on-click-bar (get-in bar [:bar :entry-id])))}))))
            draw-result
            drawable-bars))

        draw-right-label
        (fn [draw-result]
          (if-not right-label
            draw-result
            (->> draw-result
                 (draw-result-append-el
                   (dom/text
                     #js {:className "project-label project-label-summary"
                          :x (+ (:x2 (last drawable-bars))
                                (drawing-const [:project-label-offset-px]))
                          :y (+ (:y-offset draw-result)
                                (drawing-const
                                  [:project-label-vert-offset-px]))}
                    right-label)))))]
    (->> draw-result
         (draw-result-append-el
           (dom/text
             #js {:className "project-label project-label-taskname"
                  :x x-start
                  :y (+ (:y-offset draw-result)
                        (drawing-const [:project-label-vert-offset-px]))
                  :textAnchor "end"}
            text))
         (draw-bars)
         (draw-right-label)
         (draw-result-add-to-y (drawing-const [:row-spacing-px])))))

(defn format-duration
  [duration]
  (let [hours (.. duration toHours toString)
        minutes (.toString (mod (. duration toMinutes) 60))
        minutes (if (< (count minutes) 2) (str "0" minutes) minutes)]
    (str hours ":" minutes)))


(defn render-project-heading
  [label start-at-instant draw-result]
  (let [x (drawing-const [:project-heading-x-px])]
    (->> draw-result
         (draw-result-append-el
           (dom/text
             #js {:className "project-heading"
                  :x x
                  :y (:y-offset draw-result)}
            label))
         (draw-result-add-to-y
           (drawing-const [:heading-to-rows-px])))))

(defn render-project-tasks
  [render-row tasks draw-result]
  (reduce
    (fn [draw-result [task-id entries]]
      (render-row (-> entries first :task)
                  entries
                  {:right-label (format-duration
                                  (bars-sum-duration entries))}
                  draw-result))
    draw-result
    tasks))

(defn render-project
  [render-project-tasks project draw-result]
  (let [tasks (->> (:bars project)
                   (group-by :task-id)
                   (sort (fn [[_ [a & _]] [_ [b & more]]]
                           (.compareTo (:from a) (:from b)))))]
    (->> draw-result
         (render-project-heading
           (:label project)
           (get-in tasks [0 :items 0 :from]))
         (render-project-tasks tasks)
         (draw-result-add-to-y
           (drawing-const [:project-spacing-px])))))

(defn render-projects
  [projects render-row draw-result]
  (let [render-project-tasks (partial render-project-tasks render-row)
        render-project (partial render-project render-project-tasks)
        draw-projects
        (fn [draw-result]
          (reduce #(render-project %2 %1) draw-result projects))]

    (->> draw-result
         (draw-result-add-to-y
           (drawing-const [:projects :vertical-offset-px]))
         (draw-projects))))

(defn render-grid
  [iterate-day-grid selected-date height draw-result]
  (iterate-day-grid
    (fn [{:keys [left-x right-x left-instant]}
         draw-result]
      (cond->> draw-result
        (.equals (.ofInstant LocalDate left-instant)
                 selected-date)
        (draw-result-append-el
          (dom/rect
            #js {:className "selected-date-highlight"
                 :x left-x :width (- right-x left-x)
                 :y 0 :height height}))
        true
        (draw-result-append-el
          (dom/line
            #js {:className "grid-day-separator"
                 :x1 left-x :x2 left-x
                 :y1 0 :y2 height}))))
    draw-result))


(defn render-now-indicator
  [instant-to-x min-time max-time height draw-result]
  (let [now (.now Instant)
        time (.ofInstant LocalTime now)
        max-time-is-midnight? (.equals (.-MIDNIGHT LocalTime) max-time)]
    (if (or (< (.compareTo time min-time) 0)
            (and (not max-time-is-midnight?)
                 (> (.compareTo time max-time) 0)))
      draw-result
      (let [x (- (.floor js/Math (instant-to-x now)) 0.5)]
        (draw-result-append-el
          (dom/line
            #js {:className "now-indicator"
                 :x1 x :x2 x
                 :y1 0 :y2 height})
          draw-result)))))

(defn bars-filter-date
  [date bars]
  (filter
    #(let [bar-date (. LocalDate ofInstant (:from %))]
       (= 0 (.compareTo bar-date date)))
    bars))

(defn bars-filter-billable
  [billable? bars]
  (filter
    #(= billable? (:billable? %))
    bars))

(defn render-bars
  [instant-to-x bars y-offset {:keys [bar-classes] :as opts} draw-result]
  (if-not (seq bars)
    draw-result
    (reduce
      (fn [draw-result bar]
        (let [x1 (instant-to-x (:from bar))
              x2 (instant-to-x (:till bar))
              classes (string/join " "(conj bar-classes "time-line"))
              el (dom/line
                   #js {:className classes
                        :x1 x1
                        :x2 x2
                        :y1 y-offset
                        :y2 y-offset})]
          (draw-result-append-el el draw-result)))
      draw-result
      bars)))

(defn render-day-summaries
  [render-bars iterate-day-grid projects {:keys [y-offset] :as draw-result}]
  (let [all-bars (mapcat :bars projects)
        total-duration (bars-sum-duration all-bars)
        bar-offset (+ y-offset
                      (drawing-const [:day-summary :bar :y-offset-px]))
        text-offset (+ bar-offset
                       (drawing-const [:day-summary :label :y-offset-px]))
        min-hours (. Duration ofHours 8)]
    (iterate-day-grid
      (fn [{:keys [right-x left-instant]} draw-result]
        (let [date (. LocalDate ofInstant left-instant)
              day-bars (bars-filter-date date all-bars)
              billable (bars-merge (bars-filter-billable true day-bars))
              non-billable (bars-merge
                             (bars-filter-billable false day-bars))
              day-duration (bars-sum-duration day-bars)
              day-complete-class
              (if (>= (. day-duration compareTo min-hours) 0)
                "day-complete-complete"
                "day-complete-not-complete")]
          (if-not (seq day-bars)
            draw-result
            (->> draw-result
                 (render-bars
                   billable (+ bar-offset 0.5)
                   {:bar-classes ["day-summary" "billable"
                                  day-complete-class]})
                 (render-bars
                   non-billable (- bar-offset 0.5)
                   {:bar-classes ["day-summary" "non-billable"
                                  day-complete-class]})
                 (draw-result-append-el
                   (dom/text
                     #js {:className "day-summary-label"
                          :x (- right-x
                                (drawing-const
                                  [:day-summary :label :x-offset-px]))
                          :y text-offset
                          :textAnchor "end"}
                     (dom/tspan
                       #js {:className day-complete-class}
                       (format-duration day-duration))
                     (dom/tspan
                       nil
                       (let [day-billability
                             (/ (.toMinutes (bars-sum-duration billable))
                                (.toMinutes day-duration))]
                         (str " (" (.floor js/Math (* 100 day-billability))
                              "%)")))))
                 (draw-result-set-y-offset text-offset)))))
      draw-result)))


(defn get-render-fns
  [dispatch!
   selected-date-str
   projects
   selected-entry
   on-change-date]
  (let [selected-date (.parse LocalDate selected-date-str)
        min-date (find-first-monday selected-date)
        [min-time max-time] (calc-min-max-time projects)
        minutes-per-day (. min-time until max-time (. ChronoUnit -MINUTES))
        minutes-per-day (if (> minutes-per-day 0)
                          minutes-per-day
                          (+ minutes-per-day (.. Duration (ofDays 1) toMinutes)))
        pixels-per-minute (/ (drawing-const [:pixels-per-day]) minutes-per-day)

        instant-to-x (partial instant-to-x min-time min-date minutes-per-day
                              pixels-per-minute)
        iterate-day-grid (partial iterate-day-grid min-time min-date instant-to-x)
        render-row (fn [text bars opts draw-result]
                     (let [opts' (merge
                                   {:on-mouse-over-bar #(dispatch! {:action :mouse-over-entry :entry %})
                                    :on-mouse-leave-bar #(dispatch! {:action :mouse-leave-entry})
                                    :on-click-bar #(dispatch! {:action :edit-entry :entry-id %})}
                                   opts)]
                       (render-row selected-entry instant-to-x text bars opts'
                                   draw-result)))

        render-bars (partial render-bars instant-to-x)]
    {:render-projects (partial render-projects projects render-row)
     :render-grid (partial render-grid iterate-day-grid selected-date)
     :render-grid-labels (partial render-grid-labels on-change-date selected-date
                                 min-time max-time iterate-day-grid)
     :render-now-indicator (partial render-now-indicator instant-to-x min-time
                                   max-time)
     :render-day-summaries (partial render-day-summaries render-bars
                                    iterate-day-grid projects)}))

(defn render-svg
  [dispatch!
   selected-date-str
   projects
   on-change-date
   {:keys [selected-entry] :as opts}]
  (let [{:keys [render-grid-labels
                render-projects
                render-grid
                render-now-indicator
                render-day-summaries]} (get-render-fns
                                         dispatch!
                                         selected-date-str
                                         projects
                                         selected-entry
                                         on-change-date)
        has-project-data? (seq projects)

        draw-result (-> (draw-result-empty)
                        (render-grid-labels))
        draw-result (if has-project-data?
                      (->> draw-result
                           (render-projects)
                           (render-day-summaries)
                           (draw-result-add-to-y
                             (drawing-const [:canvas
                                             :bottom-padding-px])))
                      (draw-no-data-msg draw-result))
        height (:y-offset draw-result)
        grid (->> (draw-result-empty)
                  (render-grid height)
                  (render-now-indicator height))]
    (apply dom/svg
      #js {:version "1.1"
           :baseProfile "full"
           :width "100%"
           :height height}
      (dom/defs
        #js {:dangerouslySetInnerHTML
             #js {:__html (svg-glow "hour-entry-active-glow"
                                    (drawing-const [:hour-entry :active :color])
                                    :radius (drawing-const [:hour-entry :active :radius] 1)
                                    :std-dev (drawing-const [:hour-entry :active :std-dev] 1))}})
      ;TODO assign keys
      (concat (:els grid) (:els draw-result)))))
      ;(map-indexed
        ;; TODO assign proper keys
        ;(fn [idx [el attrs-or-child & more]]
          ;(let [attrs (when (map? attrs-or-child) attrs-or-child)
                ;children (concat (when-not attrs [attrs]) more)]
            ;[el (assoc attrs :key idx) children]))
        ;(do (log "grid" (:els grid))
            ;(log "draw-result" (:els draw-result))
            ;(concat (:els grid) (:els draw-result)))))))

(defn render-change-date-btns
  [{:keys [dispatch!] :as state}]
  (let [on-change-date #(dispatch! (actions/change-entry-page-date %))
        selected-date (.parse LocalDate
                              (get-in state [:project-data :selected-date]))]
    (dom/div
      #js {:className "pull-right btn-group"}
      (dom/button
        #js {:className "btn btn-default btn-xs btn-prev-period"
             :onClick (fn [] (on-change-date (.plusDays selected-date -7)))}
        (dom/span #js {:className "glyphicon glyphicon-chevron-left"}))
      (dom/button
        #js {:className "btn btn-default btn-xs btn-period-today"
             :onClick (fn [] (on-change-date (.now LocalDate)))}
        "Today")
      (dom/button
        #js {:className "btn btn-default btn-xs btn-next-period"
             :onClick (fn [] (on-change-date (.plusDays selected-date 7)))}
        (dom/span
          #js {:className "glyphicon glyphicon-chevron-right"})))))

(defn tooltip-component [state owner]
  (let [{:keys [top width left comment records]} state]
    (reify
      om/IRender
      (render [_]
        (dom/div
          #js
          {:style #js {:display (if (seq comment) "block" "none")}
           :ref "element"}
          (dom/div
            #js
            {:className "tooltip top"
             :role "tooltip"
             :style #js {:left (+ left (/ width 2))
                         :top top}}
            (dom/div #js {:className "tooltip-arrow"})
            (dom/div #js {:className "tooltip-inner"}
                     comment))))
      om/IDidUpdate
      (did-update [_ _ _]
        (let [el (aget (om/get-node owner "element") "children" 0)
              rect (get-abs-bounding-client-rect el)
              px (fn [val] (str val "px"))]
          (aset el "style" "left" (px (- (+ left (/ width 2))
                                         (/ (:width rect) 2))))
          (aset el "style" "top" (px (- top (:height rect))))
          (aset el "className" "tooltip top fade in"))))))

(defn merge-entries [merge-on to-merge]
  (let [ids (set (map :entry-id to-merge))]
    (concat (remove #(get ids (:entry-id %)) merge-on) to-merge)))

(defn render-graphic
  [{:keys [dispatch!
           project-data
           additional-entries
           selected-entry] :as state}]
  ;(log "project-data %o" project-data)
  (when (seq project-data)
    (let [union-records (merge-entries (:data project-data)
                                       additional-entries)
          svg (render-svg
                dispatch!
                (:selected-date project-data)
                (init-data union-records)
                #(dispatch! (actions/change-entry-page-date %))
                {:selected-entry selected-entry})

          html (dom/div
                 nil
                 (dom/div
                   #js {:className "row"}
                   (dom/div
                     #js {:className "col-md-12"}
                     (render-change-date-btns state))
                   (dom/div
                     #js {:id "activity-svg-container"}
                     svg)))]
      html)))

(defn get-selected-entry
  [entry-screen-form]
  (-> (state/input-entry entry-screen-form)
      (state/additional-entry) ;TODO we shouldnt depend on post-entry-form here, or do we?
      (state/input-entry->data-entry)))

(defn render-html [state owner]
  (reify
    om/IRender
    (render [_]
      (let [entries (om/observe owner (state/entries))
            entry-screen-form (om/observe owner (state/entry-screen-form))
            selected-entry (get-selected-entry entry-screen-form)
            selected-date (get-in entry-screen-form [:selected-date :date])]
        (render-graphic
          {:dispatch! (:dispatch! state)
           :selected-entry (when selected-entry (:entry-id selected-entry))
           :additional-entries (when selected-entry [selected-entry])
           :project-data {:data entries
                          :selected-date selected-date}})))))

(defn render-tooltip
  [state owner]
  (reify
    om/IRender
    (render [_]
      (let [entries (om/observe owner (state/entries))
            mouse-over-entry (om/observe owner (state/mouse-over-entry))
            entry-screen-form (om/observe owner (state/entry-screen-form))

            selected-entry (get-selected-entry entry-screen-form)
            project-data (merge-entries entries
                                        (when selected-entry [selected-entry]))
            entry-id (:entry-id mouse-over-entry)
            {:keys [left top width]} (:pos mouse-over-entry)
            comment (->> project-data
                         (filter #(= entry-id (:entry-id %)))
                         (first)
                         :comment)]
        (om/build tooltip-component
                  {:top top
                   :records project-data
                   :left left
                   :width width
                   :comment comment})))))
