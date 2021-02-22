(ns chicken-master.mocks
  (:import [java.time Instant]
           [java.time.temporal ChronoUnit]))


(defn format-date [d] (-> d str (subs 0 10)))

(defn days-range [days date ]
  (map #(.plus date % ChronoUnit/DAYS) (range days)))

;;;; Stock

(def stock-products (atom {:eggs 22 :milk 32 :cabbage 54 :carrots 11 :cows 32 :ants 21}))

(defn get-all-products [] @stock-products)
(defn save-stocks [new-products] (reset! stock-products new-products))

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
   (->> (-> (Instant/now) (.minus 50 ChronoUnit/DAYS))
        (days-range 90)
        (map (fn [date]
               [(format-date date) (repeatedly (rand-int 6) #(swap! id-counter inc))]))
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

(defn add-customer [customer-name]
  (swap! customers conj {:id (->> @customers (map :id) (apply max) inc)
                         :name customer-name})
  (fetch-stock {}))

(defn delete-customer [id]
  {:orders (swap! orders #(->> % (remove (comp #{id} :id :who second)) (into {})))
   :customers (swap! customers (partial remove (comp #{id} :id)))})

(defn day-customers [day] [day (->> @orders vals (filter (comp #{day} :day)))])

(defn get-orders [params] @orders)

(defn replace-order [id order]
  (prn id)
  (prn order)
  (println "replacing order" order)
  (let [prev-day (:day (@orders (:id order)))
        order (update order :id #(or % (swap! id-counter inc)))]
    (prn "order 1" order)
    (swap! orders assoc (:id order) order)
    (prn "order 2" (@orders (:id order)))
    (if (or (not prev-day) (= prev-day (:day order)))
      {(:day order) (->> order :day day-customers second)}
      {prev-day (->> prev-day day-customers second)
       (:day order) (->> order :day day-customers second)})))

(defn delete-order [id]
  (println "deleting order" id)
  (let [day (-> (get @orders id) :day)]
    (swap! orders #(dissoc % id))
    {day (->> day day-customers second)}))

(defn order-state [id state]
  (prn "fulfilling order" id state)
  (condp = state
    "fulfilled" (->> id (get @orders) :products (swap! stock-products #(merge-with - %1 %2)))
    "waiting" (->> id (get @orders) :products (swap! stock-products #(merge-with + %1 %2))))
  (let [day (-> (get @orders id) :day)]
    (swap! orders #(assoc-in % [id :state] (keyword state)))
    (println id (get @orders id))
    {day (->> day day-customers second)}))
