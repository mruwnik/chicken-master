(ns chicken-master.db
  (:require [clojure.string :as str]
            [config.core :refer [env]]
            [next.jdbc :as jdbc]))

(def db-uri (env :db-uri))

(defn psql-list
  ([items] (psql-list items ""))
  ([items prefix] (str "(" (str/join ", " (repeat (count items) (str prefix "?"))) ")")))

(defn create-user [name passwd]
  (jdbc/execute-one!
   db-uri
   ["INSERT INTO users (name, password) VALUES (?, crypt(?, gen_salt('bf')))" name passwd]))

(defn valid-user? [name passwd]
  (:users/id (jdbc/execute-one!
              db-uri
              ["SELECT * FROM users WHERE name = ? AND password = crypt(?, password)" name passwd])))

(defn get-by-id
  ([tx user-id table id] (get-by-id tx user-id table id :id))
  ([tx user-id table id id-column]
   (jdbc/execute-one! tx [(str "SELECT * from " (name table) " WHERE " (name id-column) " = ? AND user_id = ?")
                          id user-id])))

(comment
  (create-user "siloa" "krach")
  (valid-user? "siloa" "krach"))
