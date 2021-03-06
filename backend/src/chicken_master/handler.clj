(ns chicken-master.handler
  (:require [chicken-master.db :as db]
            [chicken-master.api :as endpoints]
            [clojure.edn :as edn]
            [config.core :refer [env]]
            [compojure.core :refer [GET defroutes routes]]
            [compojure.route :refer [resources not-found]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn- handle-edn [response]
  (if (= (get-in response [:headers "Content-Type"]) "application/edn")
    (update response :body pr-str)
    response))

(defn wrap-edn-response [handler]
  (fn
    ([request]
     (-> request handler handle-edn))
    ([request respond raise]
     (-> request handler handle-edn respond))))

(defn wrap-edn-request [handler]
  (fn
    ([request]
     (handler
      (if (= (:content-type request) "application/edn")
        (update request :body (comp edn/read-string slurp))
        request)))))

(defn authenticated? [name pass]
  (db/valid-user? name pass))

(defroutes base-routes
  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (resources "/")
  (not-found "not found"))

(def handler (routes
              (-> endpoints/all-routes
                  (wrap-basic-authentication authenticated?)
                  (wrap-cors :access-control-allow-origin (map re-pattern (env :allow-origin))
                             :access-control-allow-methods [:get :put :post :delete :options])
                  api
                  wrap-edn-request
                  wrap-edn-response)
              base-routes))
