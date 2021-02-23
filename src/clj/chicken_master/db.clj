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
  (jdbc/execute-one!
   db-uri
   [" SELECT * FROM users WHERE name = ? AND password = crypt(?, password)" name passwd]))

(comment
  (create-user "siloa" "krach")
  (valid-user? "siloa" "krach"))
