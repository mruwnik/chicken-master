(ns chicken-master.migrate
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]
            [config.core :refer [env]]))

(def config
  {:datastore  (jdbc/sql-database {:connection-uri (-> env :db-uri :jdbcUrl)})
   :migrations (jdbc/load-resources "migrations")})

(defn -main [command]
  (condp = command
    "up" (repl/migrate config)
    "down" (repl/rollback config)
    (println "up|migrate")))
