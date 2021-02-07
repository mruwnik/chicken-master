(ns chicken-master.handler
  (:require [chicken-master.mocks :as mocks]
            [clojure.edn :as edn]
            [compojure.core :refer [GET POST PUT DELETE defroutes]]
            [compojure.route :refer [resources]]
            [compojure.handler :refer [api]]
            [ring.util.response :refer [resource-response]]
            [ring.middleware.basic-authentication :refer [wrap-basic-authentication]]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn get-customers [] {:body (mocks/fetch-customers {})})
(defn add-customer [request] {:body (some-> request :body :name mocks/add-customer)})
(defn delete-customer [id] {:body (mocks/delete-customer (edn/read-string id))})

(defn get-products [_] {:body (mocks/get-all-products)})
(defn save-products [request] {:body (some-> request :body mocks/save-stocks)})

(defn get-orders [params] {:body {:orders (mocks/get-orders params)}})
(defn update-order [request]
  (let [id (some-> request :route-params :id (Integer/parseInt))
        body (some->> request :body)]
    {:body (mocks/replace-order id body)}))

(defn delete-order [id] {:body (mocks/delete-order (edn/read-string id))})
(defn set-order-state [id status] {:body (mocks/order-state (edn/read-string id) status)})

(defn get-stock [params]
  {:body
   {:customers (:body (get-customers))
    :products (:body (get-products params))}})

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
  (resources "/"))


(defn- handle-edn [response]
  (if (string? (:body response))
    response
    (-> response
        (assoc-in [:headers "Content-Type"] "application/edn")
        (update :body pr-str))))

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
  (and (= name "siloa")
       (= pass "krach")))

(def handler (-> routes
                 (wrap-basic-authentication authenticated?)
                 (wrap-cors :access-control-allow-origin [#"http://localhost:8280"]
                            :access-control-allow-methods [:get :put :post :delete :options])
                 api
                 wrap-edn-request
                 wrap-edn-response))
