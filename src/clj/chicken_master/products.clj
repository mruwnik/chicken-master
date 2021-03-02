(ns chicken-master.products
  (:require [next.jdbc :as jdbc]
            [next.jdbc.sql :as sql]
            [chicken-master.db :as db]))

(defn get-all [user-id]
  (->> (sql/query db/db-uri ["SELECT * FROM products WHERE deleted IS NULL AND user_id = ?" user-id])
       (map (fn [{:products/keys [name amount]}] [(keyword name) amount]))
       (into {})))

(defn products-map [tx user-id products]
  (when (seq products)
    (->> (map name (keys products))
         (into [(str "SELECT id, name FROM products WHERE user_id = ? AND name IN " (db/psql-list (keys products))) user-id])
         (sql/query tx)
         (map #(vector (:products/name %) (:products/id %)))
         (into {}))))

(defn update! [user-id new-products]
  (jdbc/with-transaction [tx db/db-uri]
    (doseq [[prod amount] new-products]
      (jdbc/execute! tx
                     ["INSERT INTO products (name, amount, user_id) VALUES(?, ?, ?)
                    ON CONFLICT (name, user_id) DO UPDATE SET amount = EXCLUDED.amount, deleted = NULL"
                      (name prod) amount user-id]))
    (sql/update! tx :products
                 {:deleted true}
                 (into [(str "name NOT IN " (db/psql-list (keys new-products)))]
                       (->> new-products keys (map name)))))
  (get-all user-id))
