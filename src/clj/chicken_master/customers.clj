(ns chicken-master.customers
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]
            [chicken-master.orders :as orders]))

(defn get-all []
  (->> (sql/query db/db-uri ["select * from customers where deleted is null"])
       (map (fn [{:customers/keys [id name]}] {:id id :name name}))))

(defn create! [name]
  (jdbc/execute! db/db-uri
                 ["INSERT INTO customers (name) VALUES(?) ON CONFLICT (name) DO UPDATE SET deleted = NULL"
                  name])
  {:customers (get-all)})

(defn delete! [id]
  (sql/update! db/db-uri :customers {:deleted true} {:id id})
  {:orders (orders/get-all)
   :customers (get-all)})
