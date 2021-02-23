(ns chicken-master.handler
  (:require [chicken-master.db :as db]
            [chicken-master.orders :as orders]
            [chicken-master.customers :as customers]
            [chicken-master.products :as products]
            [clojure.edn :as edn]
            [config.core :refer [env]]
            [compojure.core :refer [GET POST PUT DELETE defroutes]]
            [compojure.route :refer [resources not-found]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn as-edn [resp]
  {:headers {"Content-Type" "application/edn"}
   :body resp})

(defn get-customers [] (as-edn (customers/get-all)))
(defn add-customer [request] (as-edn (some-> request :body :name customers/create!)))
(defn delete-customer [id] (as-edn (customers/delete! (edn/read-string id))))

(defn get-products [_] (as-edn (products/get-all)))
(defn save-products [request] (as-edn (some-> request :body products/update!)))

(defn get-orders [params] (as-edn {:orders (orders/get-all)}))
(defn update-order [request]
  (let [id (some-> request :route-params :id (Integer/parseInt))
        order (-> request :body (update :id #(or % id)))]
    (as-edn (orders/replace! order))))

(defn delete-order [id] (as-edn (orders/delete! (edn/read-string id))))
(defn set-order-state [id status] (as-edn (orders/change-state! (edn/read-string id) status)))

(defn get-stock [params]
  (as-edn
   {:customers (:body (get-customers))
    :products (:body (get-products params))}))

(defroutes routes
  (GET "/stock" {params :query-params} (get-stock params))
  (GET "/customers" [] (get-customers))
  (POST "/customers" request (add-customer request))
  (DELETE "/customers/:id" [id] (delete-customer id))

  (GET "/products" request (get-products request))
  (POST "/products" request (save-products request))

  (GET "/orders" {params :query-params} (get-orders params))
  (POST "/orders" request (update-order request))
  (PUT "/orders/:id" request (update-order request))
  (DELETE "/orders/:id" [id] (delete-order id))
  (POST "/orders/:id/:status" [id status] (set-order-state id status))

  (GET "/" [] (resource-response "index.html" {:root "public"}))
  (resources "/")
  (not-found "not found"))

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

(def handler (-> routes
                 (wrap-basic-authentication authenticated?)
                 (wrap-cors :access-control-allow-origin (map re-pattern (env :allow-origin))
                            :access-control-allow-methods [:get :put :post :delete :options])
                 api
                 wrap-edn-request
                 wrap-edn-response))
