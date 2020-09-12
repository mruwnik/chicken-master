(ns chicken-master.time
  (:require
   [chicken-master.subs :as subs])
  (:import [goog.date DateTime Date Interval]))

(defn date-offset
  "Return the `date` offset by the given number of `days`"
  [date days]
  (let [new-day (new DateTime date)]
    (.add new-day (new Interval Interval/DAYS days))
    new-day))

(defn start-of-week
  "Get the start of the week for the given `date"
  [date]
  (->> (.getDay date)
       (- (subs/settings :first-day-offset))
       (date-offset date)))

(defn days-range
  "Return dates starting from `date`"
  ([date] (map (partial date-offset date) (range)))
  ([date n] (take n (days-range date))))

(defn same-day?
  "Returns true when both dates are from the same day" [d1 d2]
  (-> (new Date d1)
      (.equals (new Date d2))))

(defn today? "true when `d1` is today" [d1] (same-day? (js/Date.) d1))

(defn format-date [date]
  (str
   (->> date .getDay (nth (subs/settings :day-names)))
   " " (.getMonth date) "/" (.getDate date)))
