(ns alibi.infra.date-time
  (:require [clj-time.core :as time]
            [clj-time.format :refer [unparse-local-time unparse-local-date
                                     formatters parse-local-time
                                     parse-local-date formatter]])
  (:import [org.joda.time Seconds LocalTime Period LocalDate DateTime Duration]
           [org.joda.time.format PeriodFormat PeriodFormatterBuilder]))

(def datetime? (partial instance? DateTime))
(def local-date? (partial instance? LocalDate))
(def local-time? (partial instance? LocalTime))

(defn before? [t1 t2]
  (<= (.compareTo t1 t2) 0))

(defn ->local-time [val]
  (cond
    (local-time? val) val
    (string? val) (parse-local-time (formatters :hour-minute) val)
    (vector? val) (let [[amount unit] val] 
                    (when (= :hour unit) 
                      (time/local-time amount)))
    :else nil))

(defn to-local-date [dt]
  (cond
    (local-date? dt) dt
    (string? dt) (parse-local-date (formatters :year-month-day) dt)
    :else (.toLocalDate dt)))

(def ->local-date to-local-date)

(defn ->datetime
  ([local-date]
   (->datetime local-date (LocalTime/MIDNIGHT)))
  ([date time]
   (cond
     (string? date) (recur (->local-date date) time)
     (string? time) (recur date (->local-time time))
     (and (local-date? date)
          (local-time? time)) (.toDateTime date time)
     :else (assert false (str "invalid arguments" date time)))))


(defn to-local-time [dt]
  (.toLocalTime dt))

(defn plus [a-date [amount unit]]
  (let [period-fn  (case unit
                     :seconds time/seconds
                     :days time/days
                     nil)]
    (if period-fn
      (time/plus a-date (period-fn amount))
      (throw (ex-info (str "don't know how to parse " unit) {:unit unit})))))

(defn today []
  (time/today))

(defn tomorrow
  ([] (tomorrow (today)))
  ([a-date] (plus a-date [1 :days])))

(defn yesterday
  ([] (yesterday (today)))
  ([a-date] (plus a-date [-1 :days])))

(defn next-week
  ([] (next-week (today)))
  ([a-date] (plus a-date [7 :days])))

(defn format-time [a-time]
  (cond (datetime? a-time) (format-time (.toLocalTime a-time))
        :else (unparse-local-time (formatters :hour-minute) a-time)))

(defn int->seconds [s] (Seconds/seconds s))

(defn format-time-period [period]
  (let [period (-> period
                   (Period.)
                   (.normalizedStandard))
        formatter (-> (PeriodFormatterBuilder.)
                      (.printZeroAlways)
                      (.appendHours)
                      (.appendSuffix ":")
                      (.minimumPrintedDigits 2)
                      (.appendMinutes)
                      (.toFormatter))]
    (.print formatter period)))

(defn duration [t1 t2]
  (Duration. 
    (-> t1 .getMillisOfDay long)
    (-> t2 .getMillisOfDay long))) 

(defn duration->seconds [duration]
  (.getStandardSeconds duration))

(defn to-default-time-zone [dt]
  (time/to-time-zone dt (time/default-time-zone)))

(defn format-date
  ([date] (unparse-local-date (formatters :year-month-day) date))
  ([fmt date] (unparse-local-date (formatter fmt) date)))


(defn now []
  (time/now))

(defn ->unix [a-date]
  (quot (.getMillis a-date) 1000))

(defn unix->datetime [unix]
  (DateTime. (* 1000 unix)))


(defn str->local-date [val]
  (when (string? val)
    (try
      (parse-local-date (formatters :year-month-day) val)
      (catch Exception e nil))))

(defn str->local-time [val]
  (when (string? val)
    (try
      (parse-local-time (formatters :hour-minute) val)
      (catch Exception e nil))))

(defn time-equal?
  [t1 t2]
  (let [t1 (->local-time t1)
        t2 (->local-time t2)]
    (= t1 t2)))

(defn date-equal?
  [d1 d2]
  (let [d1 (->local-date d1)
        d2 (->local-date d2)]
    (= d1 d2)))

(defn seconds-between [t1 t2]
  (Seconds/secondsBetween t1 t2))
