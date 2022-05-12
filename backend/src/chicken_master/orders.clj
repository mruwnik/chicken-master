(ns chicken-master.orders
  (:require [next.jdbc :as jdbc]
            [next.jdbc.types :as jdbc.types]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]
            [chicken-master.products :as products]
            [chicken-master.customers :as customers]
            [chicken-master.time :as t]))

(defn assoc? [coll & key-vals]
  (->> key-vals
       (partition 2)
       (filter second)
       (reduce (fn [c [k v]] (assoc c k v)) coll)))

(defn recurrence-exception? [tx order-id day]
  (jdbc/execute-one! tx
                     ["SELECT * from recurrence_exceptions WHERE order_id = ? AND order_date = ?"
                      order-id (t/to-db-date day)]))

(defn upsert-exception! [tx order-id day state]
  (let [item {:order_id order-id :order_date (t/to-db-date day)}]
    (if (= state "waiting")
      (sql/delete! tx :recurrence_exceptions item)

      (if (recurrence-exception? tx order-id day)
        (sql/update! tx :recurrence_exceptions {:status (jdbc.types/as-other state)} item)
        (sql/insert! tx :recurrence_exceptions (assoc item :status (jdbc.types/as-other state)))))))

(defn set-dates [order updates]
  (let [recurrence (or (:recurrence updates) (:orders/recurrence order))
        day (or (:order_date updates) (:orders/order_date order))
        end-date (if recurrence (t/last-date day recurrence) day)]
    (assoc updates
           :recurrence recurrence
           :order_date (t/to-db-date day)
           :end_date (t/to-db-date end-date))))

(defn duplicate-order! [tx user-id order updates]
  (->> updates
       (merge {:user_id user-id})
       (set-dates order)
       (sql/insert! tx :orders)
       :orders/id))

(defn update-non-recurring [tx order updates]
  (let [order-id (:orders/id order)
        current-date (:orders/order_date order)
        {:keys [order_date] :as dates} (set-dates order updates)]
    (sql/update! tx :orders dates {:id order-id})

    ;; Make sure any status changes get copied over
    (when-let [exception (recurrence-exception? tx order-id current-date)]
      (upsert-exception! tx order-id current-date "waiting") ; make use of the fact that changing to `waiting` removes any statuses
      (upsert-exception! tx order-id order_date (:recurrence_exceptions/status exception)))
    order-id))

(defn update-single-item [tx user-id from-date {:orders/keys [id order_date] :as order} updates]
  (upsert-exception! tx id (t/to-db-date (or from-date order_date)) "canceled")
  (duplicate-order! tx user-id (dissoc order :orders/recurrence) (dissoc updates :recurrence)))

(defn update-from-date [tx user-id from-date {:orders/keys [id order_date recurrence] :as order} updates]
  (let [[recur-part-1 recur-part-2] (t/split-rule order_date recurrence from-date)]
    (cond
      ;; Split the order into 2, from the given date
      recur-part-2
      (do
        (sql/update! tx :orders {:end_date (t/to-db-date from-date) :recurrence recur-part-1} {:id id})
        (duplicate-order! tx user-id order (assoc updates :recurrence recur-part-2)))

      ;; No need to split the event, as it's the first one
      recur-part-1
      (sql/update! tx :orders (set-dates order updates) {:id id})

      :else nil ;; FIXME: What should happen if an invalid date is provided?
      )))

(defn upsert-order! [tx user-id customer-id {:keys [id day notes action-type order-date recurrence] :as bla}]
  (let [updates (assoc? {:customer_id customer-id}
                        :recurrence (some-> recurrence t/make-rule)
                        :notes notes
                        :order_date (some-> day t/to-db-date))
        order (db/get-by-id tx user-id :orders id)]
    (cond
      (not order)
      (duplicate-order! tx user-id order updates)

      (-> order :orders/recurrence nil?)
      (update-non-recurring tx order updates)

      (= "all" action-type)
      (do (sql/update! tx :orders (set-dates order updates) {:id id}) id)

      (= "from-here" action-type)
      (update-from-date tx user-id order-date order updates)

      :else ; single item modified
      (update-single-item tx user-id order-date order updates))))

(defn structure-order [items]
  {:id         (-> items first :orders/id)
   :notes      (-> items first :orders/notes)
   :recurrence (some-> items first :orders/recurrence t/parse-rule)
   :who        {:id   (-> items first :customers/id)
                :name (-> items first :customers/name)}
   :products   (->> items
                    (filter :products/name)
                    (reduce (fn [coll {:keys [order_products/amount order_products/price products/name]}]
                              (assoc coll (keyword name) {:amount amount :price price})) {}))})

(defn item-days
  "Get all days between `from` and `to` (inclusively) for which the order applies."
  [from to items]
  (let [{:orders/keys [recurrence order_date]} (first items)]
    (->> (t/recurrence->dates order_date (or recurrence "FREQ=MONTHLY;COUNT=1"))
         (remove #(t/before % (t/to-inst from)))
         (take-while #(not (t/after % (t/to-inst to))))
         (map #(vector (t/format-date %) :waiting))
         (into {}))))

(defn order-iterator [items days]
  (->> items
       (filter :recurrence_exceptions/status)
       (reduce (fn [coll {:recurrence_exceptions/keys [status order_date]}]
                 (assoc coll (t/format-date order_date) (keyword status)))
               days)))

(defn items->orders [from to items]
  (let [base-order (structure-order items)]
    (->> items
         (item-days from to)
         (order-iterator items)
         (map (fn [[date status]] (assoc base-order :day date :state status))))))

(def orders-query
  "SELECT o.id, o.notes, ex.status, o.order_date, o.recurrence, c.id, c.name, p.name, op.amount, op.price, ex.order_date
   FROM orders o JOIN customers c ON o.customer_id = c.id
   LEFT OUTER JOIN recurrence_exceptions ex ON o.id = ex.order_id
   LEFT OUTER JOIN order_products op ON o.id = op.order_id
   LEFT OUTER JOIN products p on p.id = op.product_id ")

(def date-filter-clause "WHERE o.end_date >= ? AND o.order_date <= ? ")
(def orders-date-query (str orders-query date-filter-clause))

(defn- get-orders
  ([tx where params] (get-orders tx t/min-date t/max-date where params))
  ([tx from to where params]
   (->> (into [(str orders-date-query (if where (str " AND " where) ""))
               (t/to-db-date from)
               (t/to-db-date to)] params)
        (sql/query tx)
        (group-by :orders/id)
        vals
        (map (partial items->orders from to))
        (apply concat)
        (filter #(t/between from (:day %) to))
        (remove (comp #{:canceled} :state)))))

(defn get-order [tx user-id id & [day]]
  (first
   (if day
     (->> (get-orders tx day day "o.id = ? AND o.user_id = ?" [id user-id])
          (filter #(= (t/format-date day) (:day %))))
     (get-orders tx "o.id = ? AND o.user_id = ?" [id user-id]))))

(defn get-all [user-id] (group-by :day (get-orders db/db-uri "o.user_id = ?" [user-id])))

(defn- orders-for-days [tx user-id & days]
  (let [days (->> days (remove nil?) (map t/to-inst))
        from (apply t/earliest days)
        to (apply t/latest days)]
    (->> (get-orders tx from to "o.user_id = ?" [user-id])
         (group-by :day)
         (merge (reduce #(assoc %1 (t/format-date %2) {}) {} days)))))

(defn get-fortnight [tx user-id & days]
  (let [fortnight-after-earliest (t/plus (apply t/earliest days) 14 :days)]
    (apply orders-for-days tx user-id fortnight-after-earliest days)))

(defn replace!
  ([user-id order] (jdbc/with-transaction [tx db/db-uri] (replace! tx user-id order)))
  ([tx user-id {:keys [who products day order-date] :as order}]
   (let [customer-id (or (:id who)
                         (customers/get-or-create-by-name tx user-id (:name who)))]
     (products/update-products-mapping! tx user-id :order
                                        (upsert-order! tx user-id customer-id order)
                                        products)
     (get-fortnight tx user-id day order-date))))

(defn change-state!
  "Update the state of the given order and also modify the number of products available:
  * when `fulfilled` decrement the number of products
  * when `waiting` increment the number (as this means a previously fulfilled order has been returned)"
  ([user-id id day state] (jdbc/with-transaction [tx db/db-uri] (change-state! tx user-id id day state)))
  ([tx user-id id day state]
   (let [order (get-order tx user-id id day)
         operator (condp = state
                    "fulfilled" "-"
                    "waiting"   "+"
                    "canceled"   "+")]
     (when (not= (:state order) (keyword state))
       ;; update product counts
       (doseq [[prod {:keys [amount]}] (:products order)]
         (jdbc/execute-one! tx
                            [(str "UPDATE products SET amount = amount " operator " ? WHERE name = ?")
                              amount (name prod)]))

       (upsert-exception! tx id day state))

     (orders-for-days tx user-id day))))

(defn- full-delete [tx user-id id]
  (when-let [{:orders/keys [order_date end_date]} (some->> id (db/get-by-id tx user-id :orders))]
    (sql/delete! tx :orders {:id id :user_id user-id})
    (get-fortnight tx user-id (t/earliest order_date end_date))))

(defn- delete-from-date [tx user-id id from-date]
  (when-let [{:orders/keys [order_date recurrence] :as order} (some->> id (db/get-by-id tx user-id :orders))]
    (if (t/same-day order_date from-date)
      ;; deleting from the first date just means removing them all
      (full-delete tx user-id id)
      ;; deleting from a given date should update the recurrence rule to end with the given date and
      ;; update the `end_date` field
      (do
        (sql/update! tx :orders
                     (set-dates order {:recurrence (t/recur-until order_date recurrence from-date)})
                     {:id id})
        (->> (t/plus from-date 14 :days)
             (t/dates-between order_date recurrence from-date)
             (apply orders-for-days tx user-id))))))

(defn delete! [user-id day action-type id]
  (jdbc/with-transaction [tx db/db-uri]
    (cond
      ;; Delete the order along with all recurrences
      (or (->> id (db/get-by-id tx user-id :orders) :orders/recurrence nil?)
          (= "all" action-type))
      (full-delete tx user-id id)

      (= "from-here" action-type)
      (delete-from-date tx user-id id day)

      ;; Only delete the one day
      :else
      (change-state! tx user-id id day "canceled"))))
