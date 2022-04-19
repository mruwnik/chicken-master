(ns chicken-master.orders
  (:require [next.jdbc :as jdbc]
            [next.jdbc.types :as jdbc.types]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]
            [chicken-master.products :as products]
            [chicken-master.customers :as customers]
            [chicken-master.time :as t]))

(defn upsert-order! [tx user-id customer-id {:keys [id day notes]}]
  (let [order {:customer_id customer-id
               :notes notes
               :order_date (some-> day t/to-db-date)
               :end_date (some-> day t/to-db-date)}]
    (if (db/get-by-id tx user-id :orders id)
      (do (sql/update! tx :orders order {:id id}) id)
      (:orders/id (sql/insert! tx :orders (assoc order :user_id user-id))))))

(defn structure-order [items]
  {:id         (-> items first :orders/id)
   :notes      (-> items first :orders/notes)
   :recurrence (-> items first :orders/recurrence)
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
    (->> (t/recurrence->dates (t/latest from order_date) (or recurrence "FREQ=MONTHLY;COUNT=1"))
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

(def date-filter-clause "WHERE o.order_date >= ? AND o.end_date <= ? ")
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
        (filter #(t/between from (:day %) to)))))

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

(defn replace! [user-id {:keys [who products] :as order}]
  (jdbc/with-transaction [tx db/db-uri]
    (let [customer-id (or (:id who)
                          (customers/get-or-create-by-name tx user-id (:name who)))
          previous-day (some->> order :id (db/get-by-id tx user-id :orders) :orders/order_date t/to-inst)]
      (products/update-products-mapping! tx user-id :order
                                         (upsert-order! tx user-id customer-id order)
                                         products)
      (orders-for-days tx user-id previous-day (some-> order :day t/parse-date)))))

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

       ;; upsert the state for the given day
       (if (jdbc/execute-one! tx
            ["SELECT * from recurrence_exceptions WHERE order_id = ? AND order_date = ?" id (t/to-db-date day)])
         (sql/update! tx :recurrence_exceptions {:status (jdbc.types/as-other state)}
                      {:order_id id :order_date (t/to-db-date day)})
         (sql/insert! tx :recurrence_exceptions {:order_id id
                                                 :order_date (t/to-db-date day)
                                                 :status (jdbc.types/as-other state)})))
     (orders-for-days tx user-id day))))

(defn delete! [user-id day id]
  (jdbc/with-transaction [tx db/db-uri]
    (if day
      ;; Only delete the one day
      (change-state! tx user-id id day "canceled")
      ;; Delete the order along with all recurrences
      (when-let [{:orders/keys [order_date end_date]} (some->> id (db/get-by-id tx user-id :orders))]
        (sql/delete! tx :orders {:id id :user_id user-id})
        (orders-for-days tx user-id order_date end_date)))))

;; (delete! 2 "2022-04-20" 240)
;; (delete! 2 nil 241)

;; (change-state! 2 240 "2022-04-20" "waiting")
;; (change-state! 2 250 "2022-04-23" "fulfilled")
;; (get-orders db/db-uri (t/to-inst #inst "2022-04-20T00:00:00Z") (t/to-inst #inst "2022-04-20T00:00:00Z") nil nil)
;; (get-orders db/db-uri (t/to-inst #inst "2022-04-23T00:00:00Z") (t/to-inst #inst "2022-04-24T00:00:00Z") nil nil)
;; (get-order db/db-uri 2 242 (t/to-inst #inst "2022-04-20T00:00:00Z"))
;; (orders-for-days db/db-uri 2 #inst "2022-04-23T00:00:00Z" #inst "2022-04-23T00:00:00Z")
;; (orders-for-days db/db-uri 2 #inst "2022-04-23T00:00:00Z")
;; (orders-for-days db/db-uri 2 "2022-04-19")
;; (get-all 2)
