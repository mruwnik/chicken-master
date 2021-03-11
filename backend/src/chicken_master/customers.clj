(ns chicken-master.customers
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]))

(defn get-all [user-id]
  (->> (sql/query db/db-uri ["select * from customers where deleted is null AND user_id = ?" user-id])
       (map (fn [{:customers/keys [id name]}] {:id id :name name
                                              :product-groups {"bla" {:eggs 2 :carrots 13}
                                                               "ble" {:eggs 12 :milk 3}}}))))

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
