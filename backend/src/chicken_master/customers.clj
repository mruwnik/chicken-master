(ns chicken-master.customers
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.products :as products]
            [chicken-master.db :as db]))



(defn insert-products [coll {:keys [id name]} products]
  (->> products
       (reduce #(assoc %1 (-> %2 :products/name keyword) (:customer_group_products/amount %2)) {})
       (assoc {:id id} :products)
       (assoc coll name)))

(defn extract-product-groups [client products]
  (if-not products
    client
    (->> products
         (filter :customer_groups/name)
         (group-by (fn [{:customer_groups/keys [id name]}] {:id id :name name}))
         (reduce-kv insert-products {})
         (assoc client :product-groups))))

(def users-select-query
  "SELECT * FROM customers c
   LEFT OUTER JOIN customer_groups cg on c.id = cg.customer_id
   LEFT OUTER JOIN customer_group_products cgp on cg.id = cgp.customer_group_id
   LEFT OUTER JOIN products p ON p.id = cgp.product_id
   WHERE c.deleted IS NULL aND c.user_id = ?")

(defn get-all [user-id]
  (->> (sql/query db/db-uri [users-select-query user-id])
       (group-by (fn [{:customers/keys [id name]}] {:id id :name name}))
       (map (partial apply extract-product-groups))))

(defn create! [user-id name]
  (jdbc/execute! db/db-uri
                 ["INSERT INTO customers (name, user_id) VALUES(?, ?) ON CONFLICT (name, user_id) DO UPDATE SET deleted = NULL"
                  name user-id])
  {:customers (get-all user-id)})

(defn delete! [user-id id]
  (sql/update! db/db-uri :customers {:deleted true} {:id id :user_id user-id}))


(defn get-by-name [tx user-id name]
  (:customers/id (db/get-by-id tx user-id :customers name :name)))

(defn get-or-create-by-name [tx user-id name]
  (if-let [id (:customers/id (db/get-by-id tx user-id :customers (:name name) :name))]
    id
    (do (create! user-id name)
        (get-by-name tx user-id name))))


(defn upsert-customer-group! [tx user-id customer-id {:keys [id name]}]
  (if (jdbc/execute-one! tx ["SELECT * FROM customer_groups WHERE user_id = ? AND customer_id = ? AND id =?"
                             user-id customer-id id])
    (do (sql/update! tx :customer_groups {:name name} {:id id}) id)
    (->> {:user_id user-id :name name :customer_id customer-id}
         (sql/insert! tx :customer_groups)
         :customer_groups/id)))

(defn save-product-group [user-id customer-id group]
  (jdbc/with-transaction [tx db/db-uri]
    (products/update-products-mapping!
     tx user-id :customer_group
     (upsert-customer-group! tx user-id customer-id group)
     (:products group))))
