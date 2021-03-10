(ns chicken-master.time
  (:import [goog.date Date Interval]))

(def settings (atom settings))
(defn update-settings [new-settings] (reset! settings new-settings))

(defn today [] (new Date))

(defn parse-date [date] (new Date (js/Date. date)))

(defn date-offset
  "Return the `date` offset by the given number of `days`"
  [date days]
  (let [new-day (new Date date)]
    (.add new-day (new Interval Interval/DAYS days))
    new-day))

(defn start-of-week
  "Get the start of the week for the given `date"
  [date]
  (let [offset (mod (+ 7 (.getDay date) (- (get @settings :first-day-offset 0))) 7)]
    (date-offset date (- offset))))

(defn days-range
  "Return dates starting from `date`"
  ([date] (map (partial date-offset date) (range)))
  ([n date] (take n (days-range date))))

(defn before? [d1 d2] (< (Date/compare d1 d2) 0))
(defn after? [d1 d2] (> (Date/compare d1 d2) 0))

(defn same-day?
  "Returns true when both dates are from the same day" [d1 d2]
  (-> (new Date d1)
      (.equals (new Date d2))))

(defn today? "true when `d1` is today" [d1] (same-day? (js/Date.) d1))

(defn format-date [date]
  (when (get @settings :show-date)
    (if (get @settings :show-day-name-with-date)
      (str
       (->> date .getDay (nth (get @settings :day-names)))
       " " (inc (.getMonth date)) "/" (.getDate date))
      (str (inc (.getMonth date)) "/" (.getDate date)))))

(defn iso-date [date] (.toIsoString ^js/goog.date.Date date true))

(defn get-weeks [from n]
  (->> from
       parse-date
       start-of-week
       (days-range (* 7 n))
       (map iso-date)))


(comment
  (with-redefs [settings {:first-day-offset 0}]
    (= (->> (parse-date "2020-11-22") start-of-week iso-date) "2020-11-22")
    (filter #(= (-> (parse-date %) start-of-week iso-date) "2020-11-22")
            ["2020-11-22" "2020-11-23" "2020-11-24" "2020-11-25" "2020-11-26" "2020-11-27" "2020-11-28"]))

  (with-redefs [settings {:first-day-offset 1}]
    (= (->> (parse-date "2020-11-22") start-of-week iso-date) "2020-11-16")
    (filter #(= (-> (parse-date %) start-of-week iso-date) "2020-11-23")
            ["2020-11-23" "2020-11-24" "2020-11-25" "2020-11-26" "2020-11-27" "2020-11-28" "2020-11-29" ]))
)
