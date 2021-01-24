(ns chicken-master.orders
  (:require [chicken-master.time :as time]))

;;;;;;;; Backend mocks

(def id-counter (atom -1))
(def days (atom
           (->> (time/date-offset (new js/Date) -50)
               (time/days-range 90)
               (map (fn [date]
                      [(time/iso-date date) (repeatedly (rand-int 6) #(swap! id-counter inc))]))
               (into {}))))
(def notes ["bezglutenowy"
            "tylko z robakami"
            "przyjdzie wieczorem"
            "wisi 2.50"
            "chciała ukraść kozę"])
(def products (atom [:eggs :milk :cabbage :carrots]))
(def customer-names (atom ["mr.blobby (649 234 234)" "da police (0118 999 881 999 119 725 123123 12 3123 123 )" "johnny"]))

(def customers
  (atom
   (->> @days
        (map (fn [[day ids]]
               (map (fn [i]
                      {:id i :day day
                       :notes (when (> (rand) 0.7) (rand-nth notes))
                       :state :waiting
                       :who (rand-nth @customer-names)
                       :products (->> @products
                                      (random-sample 0.4)
                                      (map #(vector % (rand-int 10)))
                                      (into {}))
                       }) ids)
               ))
        flatten
        (map #(vector (:id %) %))
        (into {}))))

(defn- day-customers [day] [day (->> day (get @days) (map (partial get @customers)))])
(defn- days-between [from to]
  (time/days-range
   (int (/ (- (time/parse-date to) (time/parse-date from)) (* 24 3600000)))
   (time/parse-date from)))

;;; Actual stuff

(defn fetch-days [{:keys [from to]}]
  (->> (days-between from to)
       (map time/iso-date)
       (map day-customers)
       (into {})))

(defn- replace-order [order]
  (println "replacing order" order)
  (let [order (update order :id #(or % (swap! id-counter inc)))]
    (swap! days (fn [ds] (update ds (:day order) #(distinct (conj % (:id order))))))
    (swap! customers #(assoc % (:id order) order))
    {(->> order :day) (->> order :day day-customers second)}))

(defn- delete-order [{id :id}]
  (println "deleting order" id)
  (let [day (-> (get @customers id) :day)]
    (swap! days (fn [ds] (update ds day (partial remove #{id}))))
    (swap! customers #(dissoc % id))
    {day (->> day day-customers second)}))

(defn- order-state [{id :id state :state}]
  (println "fulfilling order" id)
  (let [day (-> (get @customers id) :day)]
    (swap! customers #(assoc-in % [id :state] state))
    (println id (get @customers id))
    {day (->> day day-customers second)}))

(comment
(replace-order
 {:id 194, :day "2020-11-21", :hour "02:12", :who "mr.blobby (649 234 234)", :products {:eggs 13 :milk 4 :cabbage 7}})
)
