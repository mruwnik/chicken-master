(ns chicken-master.time
  (:require [chicken-master.config :refer [settings]])
  (:import [goog.date DateTime Date Interval]))

(defn date-offset
  "Return the `date` offset by the given number of `days`"
  [date days]
  (let [new-day (new Date date)]
    (.add new-day (new Interval Interval/DAYS days))
    new-day))

(defn start-of-week
  "Get the start of the week for the given `date"
  [date]
  (->> (.getDay date)
       (- (settings :first-day-offset))
       (date-offset date)))

(defn days-range
  "Return dates starting from `date`"
  ([date] (map (partial date-offset date) (range)))
  ([n date] (take n (days-range date))))

(defn same-day?
  "Returns true when both dates are from the same day" [d1 d2]
  (-> (new Date d1)
      (.equals (new Date d2))))

(defn today? "true when `d1` is today" [d1] (same-day? (js/Date.) d1))

(defn format-date [date]
  (str
   (->> date .getDay (nth (settings :day-names)))
   " " (.getMonth date) "/" (.getDate date)))
