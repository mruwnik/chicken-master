(ns chicken-master.customers
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]
            [chicken-master.orders :as orders]))

(defn get-all [user-id]
  (->> (sql/query db/db-uri ["select * from customers where deleted is null AND user_id = ?" user-id])
       (map (fn [{:customers/keys [id name]}] {:id id :name name}))))

(defn create! [user-id name]
  (jdbc/execute! db/db-uri
                 ["INSERT INTO customers (name, user_id) VALUES(?, ?) ON CONFLICT (name, user_id) DO UPDATE SET deleted = NULL"
                  name user-id])
  {:customers (get-all user-id)})

(defn delete! [user-id id]
  (sql/update! db/db-uri :customers {:deleted true} {:id id :user_id user-id})
  {:orders (orders/get-all user-id)
   :customers (get-all user-id)})
