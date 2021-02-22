(ns chicken-master.db
  (:require [clojure.string :as str]
            [next.jdbc :as jdbc]
            [next.jdbc.types :as jdbc.types]
            [next.jdbc.sql :as sql]
            [chicken-master.time :as t]))

(def db-uri {:jdbcUrl (or (System/getenv "DB_URI") "jdbc:postgresql://localhost/postgres?user=postgres&password=mysecretpassword")})

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
