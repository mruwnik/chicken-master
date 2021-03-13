(ns chicken-master.api
  (:require [chicken-master.orders :as orders]
            [chicken-master.customers :as customers]
            [chicken-master.products :as products]
            [clojure.edn :as edn]
            [compojure.core :refer [GET POST PUT DELETE defroutes]]))

(defn as-edn [resp]
  {:headers {"Content-Type" "application/edn"}
   :body resp})

(defn- values-for-kind [user-id kind]
  (when-let [getter ({:customers customers/get-all
                      :products products/get-all
                      :orders orders/get-all} kind)]
    (getter user-id)))

(defn get-values [user-id kinds]
  (as-edn (reduce #(assoc %1 %2 (values-for-kind user-id %2)) {} kinds)))

(defn get-customers [user-id] (get-values user-id [:customers]))
(defn add-customer [{:keys [body basic-authentication]}]
  (some->> body :name (customers/create! basic-authentication) as-edn))
(defn save-product-group [user-id customer-id body]
  (customers/save-product-group user-id (Integer/parseInt customer-id) body)
  (get-customers user-id))

(defn delete-customer [user-id id]
  (->> id edn/read-string (customers/delete! user-id))
  (get-values user-id [:orders :customers]))

(defn get-products [user-id] (get-values user-id [:products]))
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

(defn get-stock [user-id] (get-values user-id [:customers :products]))

(defroutes all-routes
  (GET "/stock" [:as {user-id :basic-authentication}] (get-stock user-id))
  (GET "/customers" [:as {user-id :basic-authentication}] (get-customers user-id))
  (POST "/customers" request (add-customer request))
  (DELETE "/customers/:id" [id :as {user-id :basic-authentication}] (delete-customer user-id id))
  (POST "/customers/:id/product-group" [id :as {user-id :basic-authentication body :body}]
        (save-product-group user-id id body))

  (GET "/products" request (get-products request))
  (POST "/products" request (save-products request))

  (GET "/orders" [:as {user-id :basic-authentication}] (get-orders user-id))
  (POST "/orders" request (update-order request))
  (PUT "/orders/:id" request (update-order request))
  (DELETE "/orders/:id" [id :as {user-id :basic-authentication}] (delete-order user-id id))
  (POST "/orders/:id/:status" [id status :as {user-id :basic-authentication}] (set-order-state user-id id status)))
