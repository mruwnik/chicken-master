(ns chicken-master.products
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]))

(defn get-all []
  (->> (sql/query db/db-uri ["select * from products where deleted is null"])
       (map (fn [{:products/keys [name amount]}] [(keyword name) amount]))
       (into {})))

(defn products-map [tx products]
  (->> (map name (keys products))
       (into [(str "SELECT id, name from products where name IN " (db/psql-list (keys products)))])
       (sql/query tx)
       (map #(vector (:products/name %) (:products/id %)))
       (into {})))

(defn update! [new-products]
  (jdbc/with-transaction [tx db/db-uri]
    (doseq [[prod amount] new-products]
      (jdbc/execute! tx
                     ["INSERT INTO products (name, amount) VALUES(?, ?)
                    ON CONFLICT (name) DO UPDATE SET amount = EXCLUDED.amount, deleted = NULL"
                      (name prod) amount]))
    (sql/update! tx :products
                 {:deleted true}
                 (into [(str "name NOT IN " (db/psql-list (keys new-products)))]
                       (->> new-products keys (map name)))))
  (get-all))
