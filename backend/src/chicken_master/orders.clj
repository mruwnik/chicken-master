(ns chicken-master.orders
  (:require [next.jdbc :as jdbc]
            [next.jdbc.types :as jdbc.types]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]
            [chicken-master.products :as products]
            [chicken-master.customers :as customers]
            [chicken-master.time :as t]))

(defn upsert-order! [tx user-id customer-id {:keys [id day state notes]}]
  (let [order {:customer_id customer-id
               :notes notes
               :status (some-> (or state "waiting") name jdbc.types/as-other)
               :order_date (some-> day t/parse-date t/inst->timestamp)}]
    (if (db/get-by-id tx user-id :orders id)
      (do (sql/update! tx :orders order {:id id}) id)
      (:orders/id (sql/insert! tx :orders (assoc order :user_id user-id))))))


(defn- structure-order [items]
  {:id    (-> items first :orders/id)
   :notes (-> items first :orders/notes)
   :state (-> items first :orders/status keyword)
   :day   (-> items first :orders/order_date (.toInstant) str (subs 0 10))
   :who   {:id   (-> items first :customers/id)
           :name (-> items first :customers/name)}
   :products (into {}
                   (for [{:keys [order_products/amount products/name]} items]
                     [(keyword name) amount]))})

(def orders-query
  "SELECT o.id, o.notes, o.status, o.order_date, c.id, c.name, p.name, op.amount
   FROM orders o JOIN customers c ON o.customer_id = c.id
   JOIN order_products op ON o.id = op.order_id
   JOIN products p on p.id = op.product_id ")

(defn- get-orders [tx where params]
  (->> (into [(if where (str orders-query where) orders-query)] params)
       (sql/query tx)
       (group-by :orders/id)
       vals
       (map structure-order)))

(defn get-order [tx user-id id]
  (first (get-orders tx "WHERE o.id = ? AND o.user_id = ?" [id user-id])))

(defn get-all [user-id] (group-by :day (get-orders db/db-uri "WHERE o.user_id = ?" [user-id])))

(defn- orders-for-days [tx user-id & days]
  (let [days (remove nil? days)]
    (->> days
         (map t/inst->timestamp)
         (map jdbc.types/as-date)
         (into [user-id])
         (get-orders tx (str "WHERE o.user_id = ? AND o.order_date::date IN " (db/psql-list days)))
         (group-by :day)
         (merge (reduce #(assoc %1 (t/format-date %2) {}) {} days)))))

(defn replace! [user-id {:keys [who products] :as order}]
  (jdbc/with-transaction [tx db/db-uri]
    (let [customer-id (or (:id who)
                          (customers/get-by-name tx user-id who))
          products-map (products/products-map tx user-id products)
          previous-day (some->> order :id (db/get-by-id tx user-id :orders) :orders/order_date (.toInstant))
          order-id (upsert-order! tx user-id customer-id order)]
      (sql/delete! tx :order_products {:order_id order-id})
      (sql/insert-multi! tx :order_products
                         [:order_id :product_id :amount]
                         (for [[n amount] products
                               :let [product-id (-> n name products-map)]
                               :when product-id]
                           [order-id product-id amount]))
      (orders-for-days tx user-id previous-day (some-> order :day t/parse-date)))))

(defn delete! [user-id id]
  (jdbc/with-transaction [tx db/db-uri]
    (let [day (some->> id (db/get-by-id tx user-id :orders) :orders/order_date (.toInstant))]
      (sql/delete! tx :orders {:id id :user_id user-id})
      (when day (orders-for-days tx user-id day)))))

(defn change-state!
  "Update the state of the given order and also modify the number of products available:
  * when `fulfilled` decrement the number of products
  * when `waiting` increment the number (as this means a previously fulfilled order has been returned)"
  [user-id id state]
  (jdbc/with-transaction [tx db/db-uri]
    (let [order (get-order tx user-id id)
          operator (condp = state
                     "fulfilled" "-"
                     "waiting"   "+")]
      (when (not= (:state order) (keyword state))
        (doseq [[prod amount] (:products order)]
          (jdbc/execute-one! tx
                             [(str "UPDATE products SET amount = amount " operator " ? WHERE name = ?")
                              amount (name prod)]))
        (sql/update! tx :orders {:status (jdbc.types/as-other state)} {:id id}))
      (orders-for-days tx user-id (-> order :day t/parse-date)))))
