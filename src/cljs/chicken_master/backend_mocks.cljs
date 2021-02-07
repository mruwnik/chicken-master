(ns chicken-master.backend-mocks
  (:require [chicken-master.time :as time]))

;;;; Stock

(def stock-products (atom {:eggs 22 :milk 32 :cabbage 54 :carrots 11 :cows 32 :ants 21}))

(defn get-all-products [] @stock-products)
(defn save-stocks [new-products]
  (reset! stock-products new-products))

;;; Orders

(def id-counter (atom -1))
(def notes ["bezglutenowy"
            "tylko z robakami"
            "przyjdzie wieczorem"
            "wisi 2.50"
            "chciała ukraść kozę"])
(def products (atom [:eggs :milk :cabbage :carrots]))
(def customers (atom [{:id 1 :name "mr.blobby (649 234 234)"}
                      {:id 2 :name "da police (0118 999 881 999 119 725 123123 12 3123 123 )"}
                      {:id 3 :name "johnny"}]))
(def orders
  (atom
   (->> (time/date-offset (new js/Date) -50)
        (time/days-range 90)
        (map (fn [date]
               [(time/iso-date date) (repeatedly (rand-int 6) #(swap! id-counter inc))]))
        (map (fn [[day ids]]
               (map (fn [i]
                      {:id i :day day
                       :notes (when (> (rand) 0.7) (rand-nth notes))
                       :state :waiting
                       :who (rand-nth @customers)
                       :products (->> @products
                                      (random-sample 0.4)
                                      (map #(vector % (rand-int 10)))
                                      (into {}))
                       }) ids)
               ))
        flatten
        (map #(vector (:id %) %))
        (into {}))))


(defn fetch-customers [_]
  @customers)

(defn fetch-stock [params]
  {:customers (fetch-customers params)
   :products (get-all-products)})

(defn add-customer [{:keys [name] :as params}]
  (prn name)
  (swap! customers conj {:id (->> @customers (map :id) (apply max) inc)
                         :name name})
  (prn @customers)
  (fetch-stock params))

(defn delete-customer [id]
  {:orders (swap! orders #(->> % (remove (comp #{id} :id :who second)) (into {})))
   :customers (swap! customers (partial remove (comp #{id} :id)))})


(defn- day-customers [day] [day (->> @orders vals (filter (comp #{day} :day)))])
(defn- days-between [from to]
  (time/days-range
   (int (/ (- (time/parse-date to) (time/parse-date from)) (* 24 3600000)))
   (time/parse-date from)))

(defn fetch-orders [{:keys [from to]}]
  {:orders @orders})

(defn- replace-order [id order]
  (println "replacing order" order)
  (let [prev-day (:day (get @orders id))
        order (update order :id #(or % (swap! id-counter inc)))]
    (swap! orders assoc (:id order) order)
    (if prev-day
      {prev-day (->> prev-day day-customers second)
       (:day order) (->> order :day day-customers second)}
      {(:day order) (->> order :day day-customers second)})))

(defn- delete-order [id]
  (println "deleting order" id (get @orders id))
  (let [day (-> (get @orders id) :day)]
    (swap! orders #(dissoc % id))
    {day (->> day day-customers second)}))

(defn- order-state [{id :id state :state :as bla}]
  (prn "fulfilling order" id state bla)
  (condp = state
    :fulfilled (->> id (get @orders) :products (swap! stock-products #(merge-with - %1 %2)))
    :waiting (->> id (get @orders) :products (swap! stock-products #(merge-with + %1 %2))))
  (let [day (-> (get @orders id) :day)]
    (swap! orders #(assoc-in % [id :state] state))
    (println id (get @orders id))
    {day (->> day day-customers second)}))


(comment
(replace-order
 {:id 194, :day "2020-11-21", :hour "02:12", :who "mr.blobby (649 234 234)", :products {:eggs 13 :milk 4 :cabbage 7}})
)
