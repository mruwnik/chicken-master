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

(defn get-customers [user-id] (as-edn (customers/get-all user-id)))
(defn add-customer [{:keys [body basic-authentication]}]
  (as-edn (some->> body :name (customers/create! basic-authentication))))
(defn delete-customer [user-id id] (->> id edn/read-string (customers/delete! user-id) as-edn))

(defn get-products [user-id] (as-edn (products/get-all user-id)))
(defn save-products [{:keys [body basic-authentication]}]
  (some->> body (products/update! basic-authentication) (assoc {} :products) as-edn))

(defn get-orders [user-id] (as-edn (orders/get-all user-id)))
(defn update-order [request]
  (let [user-id (:basic-authentication request)
        id (some-> request :route-params :id (Integer/parseInt))
        order (-> request :body (update :id #(or % id)))]
    (as-edn (orders/replace! user-id order))))

(defn delete-order [user-id id] (->> id edn/read-string (orders/delete! user-id) as-edn))
(defn set-order-state [user-id id status] (as-edn (orders/change-state! user-id (edn/read-string id) status)))

(defn get-stock [user-id]
  (as-edn
   {:customers (:body (get-customers user-id))
    :products (:body (get-products user-id))}))

(defroutes routes
  (GET "/stock" [:as {user-id :basic-authentication}] (get-stock user-id))
  (GET "/customers" [:as {user-id :basic-authentication}] (get-customers user-id))
  (POST "/customers" request (add-customer request))
  (DELETE "/customers/:id" [id :as {user-id :basic-authentication}] (delete-customer user-id id))

  (GET "/products" request (get-products request))
  (POST "/products" request (save-products request))

  (GET "/orders" [:as {user-id :basic-authentication}] (get-orders user-id))
  (POST "/orders" request (update-order request))
  (PUT "/orders/:id" request (update-order request))
  (DELETE "/orders/:id" [id :as {user-id :basic-authentication}] (delete-order user-id id))
  (POST "/orders/:id/:status" [id status :as {user-id :basic-authentication}] (set-order-state user-id id status))

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
