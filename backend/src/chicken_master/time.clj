(ns chicken-master.time
  (:import [java.time Instant LocalDate ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.sql Timestamp]
           [org.dmfs.rfc5545.recur RecurrenceRule Freq]
           [org.dmfs.rfc5545 DateTime]))

(defn parse-date [date]
  (if (= (count date) 10)
    (-> date (LocalDate/parse) (.atStartOfDay) (.toInstant ZoneOffset/UTC))
    (Instant/parse date)))

(defprotocol TimeHelpers
  (to-inst [d])
  (to-db-date [d])
  (format-date [date])
  (before [d1 d2])
  (after [d1 d2]))

(extend-type Instant
  TimeHelpers
  (to-inst [d] d)
  (to-db-date [d] (Timestamp/from d))
  (format-date [date]
    (-> DateTimeFormatter/ISO_LOCAL_DATE
        (.withZone ZoneOffset/UTC)
        (.format date)))
  (before [d1 d2] (.isBefore d1 d2))
  (after [d1 d2] (.isBefore d2 d1)))

(extend-type java.util.Date
  TimeHelpers
  (to-inst [d] (.toInstant d))
  (to-db-date [d] (-> d to-inst to-db-date))
  (format-date [date] (format-date (to-inst date)))
  (before [d1 d2] (< (.compareTo d1 d2) 0))
  (after [d1 d2] (> (.compareTo d1 d2) 0)))

(extend-type java.lang.String
  TimeHelpers
  (to-inst [d] (parse-date d))
  (to-db-date [d] (-> d to-inst to-db-date))
  (format-date [date] (format-date (to-inst date)))
  (before [d1 d2] (before (to-inst d1) (to-inst d2)))
  (after [d1 d2] (after (to-inst d1) (to-inst d2))))

(defn earliest [& ds] (->> ds (map to-inst) (sort before) first))
(defn latest [& ds] (->> ds (map to-inst) (sort after) first))
(defn between [d1 d2 d3] (and (not (before d2 d1)) (not (after d2 d3))))
(defn same-day [d1 d2]
  (when (and d1 d2)
    (= (format-date d1) (format-date d2))))

(defn now [] (Instant/now))
(def min-date (parse-date "2020-01-01"))
(def max-date (.plusSeconds (now) (* 40 356 24 60 60))) ; 40 years from now - can't be bothered to do this properly...

;; Recurrence helpers

(defn to-recur-datetime [d] (-> d to-inst (.toEpochMilli) (DateTime.)))
(defn recurrence->dates [start rule]
  (let [iterator (.iterator (RecurrenceRule. rule) (to-recur-datetime start))]
    (take-while identity
                (repeatedly #(when (.hasNext iterator)
                               (-> iterator (.nextDateTime) (.getTimestamp) (Instant/ofEpochMilli)))))))

(defn next-date
  "Get the next date after `day`"
  [start rule day]
  (->> (recurrence->dates (to-inst start) rule)
       (filter (partial before (to-inst day)))
       first))

(defn last-date
  "Get the end date for the given rule"
  [start rule]
  (->> (recurrence->dates (to-inst start) rule)
       (take-while #(before % max-date))
       last))

(defn recurrence-pos
  "The index of the day in the sequence for `day`."
  [start rule day]
  (->> (recurrence->dates (to-inst start) rule)
       (keep-indexed (fn [i d] (when (same-day d day) i)))
       first))

(def freq-units {"day" Freq/DAILY "week" Freq/WEEKLY "month" Freq/MONTHLY "year" Freq/YEARLY})
(defn set-freq [rule freq]
  (.toString
   (if rule
     (doto (RecurrenceRule. rule) (.setFreq (freq-units freq Freq/WEEKLY) true))
     (RecurrenceRule. (freq-units freq Freq/WEEKLY)))))
(defn get-freq [rule]
  (-> rule
      (RecurrenceRule.)
      (.getFreq)
      ((clojure.set/map-invert freq-units))))

(defn get-interval [rule] (.getInterval (RecurrenceRule. rule)))
(defn set-interval [rule interval]
  (let [rule (RecurrenceRule. rule)]
    (.setInterval rule interval)
    (.toString rule)))

(defn get-count [rule] (.getCount (RecurrenceRule. rule)))
(defn set-count [rule count]
  (let [rule (RecurrenceRule. rule)]
    (.setCount rule count)
    (.toString rule)))

(defn get-until [rule]
  (some-> rule (RecurrenceRule.) (.getUntil) (.getTimestamp) (Instant/ofEpochMilli)))
(defn set-until [rule until]
  (let [rule (RecurrenceRule. rule)]
    (.setUntil rule (to-recur-datetime until))
    (.toString rule)))

(defn split-rule [start rule day]
  (let [pos (recurrence-pos start rule day)]
    (cond
      ;; FIXME: think this through...
      (nil? pos) nil

      ;; the first date is the one to split on - so just leave it as is
      (zero? pos) [rule]

      (get-count rule)
      [(set-count rule pos) (set-count rule (- (get-count rule) pos))]

      (get-until rule)
      [(set-until rule day) rule]

      :else nil)))

(defn make-rule [{:keys [times until unit every]}]
  (let [rule (-> nil
                 (set-freq unit)
                 (set-interval every))]
    (if times
      (set-count rule times)
      (set-until rule until))))

(defn parse-rule [rule]
  {:times (get-count rule)
   :until (get-until rule)
   :unit (get-freq rule)
   :every (get-interval rule)})
