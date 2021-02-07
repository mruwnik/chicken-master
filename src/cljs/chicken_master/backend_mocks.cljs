(ns chicken-master.backend-mocks
  (:require [chicken-master.time :as time]))

(defn set-item!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) key val))

(defn get-item
  "Returns value of `key' from browser's localStorage."
  [key]
  (cljs.reader/read-string (.getItem (.-localStorage js/window) key)))

(defn remove-item!
  "Remove the browser's localStorage value for the given `key`"
  [key]
  (.removeItem (.-localStorage js/window) key))
;;;; Stock

(defn get-all-products [] (get-item :stock-products))
(defn save-stocks [new-products]
  (set-item! :stock-products new-products))

;;; Orders
(def notes ["bezglutenowy"
            "tylko z robakami"
            "przyjdzie wieczorem"
            "wisi 2.50"
            "chciała ukraść kozę"])

(defn storage-swap! [val fun & args]
  (set-item! val (apply fun (get-item val) args))
  (get-item val))

(defn purge-items []
  (doseq [item [:stock-products :products :customers :orders :settings]]
    (remove-item! item))
  (set-item! :id-counter -1))

(defn generate-items []
  (set-item! :settings {})
  (set-item! :stock-products {:eggs 22 :milk 32 :cabbage 54 :carrots 11 :cows 32 :ants 21})
  (set-item! :id-counter -1)

  (set-item! :products [:eggs :milk :cabbage :carrots])
  (set-item! :customers [{:id 1 :name "mr.blobby (649 234 234)"}
                         {:id 2 :name "da police (0118 999 881 999 119 725 123123 12 3123 123 )"}
                         {:id 3 :name "johnny"}])
  (set-item! :orders
              (->> (time/date-offset (new js/Date) -50)
                   (time/days-range 90)
                   (map (fn [date]
                          [(time/iso-date date) (repeatedly (rand-int 6) #(storage-swap! :id-counter inc))]))
                   (map (fn [[day ids]]
                          (map (fn [i]
                                 {:id i :day day
                                  :notes (when (> (rand) 0.7) (rand-nth notes))
                                  :state :waiting
                                  :who (rand-nth (get-item :customers))
                                  :products (->> (get-item :products)
                                                 (random-sample 0.4)
                                                 (map #(vector % (rand-int 10)))
                                                 (into {}))
                                  }) ids)
                          ))
                   flatten
                   (map #(vector (:id %) %))
                   (into {}))))

(defn fetch-customers [_]
  (get-item :customers))

(defn fetch-stock [params]
  {:customers (fetch-customers params)
   :products (get-all-products)})

(defn add-customer [{:keys [name] :as params}]
  (prn name)
  (storage-swap! :customers conj {:id (->> (get-item :customers) (map :id) (apply max) inc)
                                 :name name})
  (prn (get-item :customers))
  (fetch-stock params))

(defn delete-customer [id]
  {:orders (storage-swap! :orders #(->> % (remove (comp #{id} :id :who second)) (into {})))
   :customers (storage-swap! :customers (partial remove (comp #{id} :id)))})


(defn- day-customers [day] [day (->> :orders get-item vals (filter (comp #{day} :day)))])
(defn- days-between [from to]
  (time/days-range
   (int (/ (- (time/parse-date to) (time/parse-date from)) (* 24 3600000)))
   (time/parse-date from)))

(defn fetch-orders [{:keys [from to]}]
  {:orders (get-item :orders)})

(defn- replace-order [id order]
  (println "replacing order" order)
  (let [prev-day (:day (get (get-item :orders) id))
        order (update order :id #(or % (storage-swap! :id-counter inc)))]
    (storage-swap! :orders assoc (:id order) order)
    (if prev-day
      {prev-day (->> prev-day day-customers second)
       (:day order) (->> order :day day-customers second)}
      {(:day order) (->> order :day day-customers second)})))

(defn- delete-order [id]
  (println "deleting order" id (get (get-item :orders) id))
  (let [day (-> (get (get-item :orders) id) :day)]
    (storage-swap! :orders #(dissoc % id))
    {day (->> day day-customers second)}))

(defn- order-state [{id :id state :state :as bla}]
  (prn "fulfilling order" id state bla)
  (condp = state
    :fulfilled (->> id (get (get-item :orders)) :products (storage-swap! :stock-products #(merge-with - %1 %2)))
    :waiting (->> id (get (get-item :orders)) :products (storage-swap! :stock-products #(merge-with + %1 %2))))
  (let [day (-> (get (get-item :orders) id) :day)]
    (storage-swap! :orders #(assoc-in % [id :state] state))
    (println id (get (get-item :orders) id))
    {day (->> day day-customers second)}))


(comment
(replace-order
 {:id 194, :day "2020-11-21", :hour "02:12", :who "mr.blobby (649 234 234)", :products {:eggs 13 :milk 4 :cabbage 7}})
)
