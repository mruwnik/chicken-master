(ns chicken-master.events
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.db :as db]
   [chicken-master.time :as time]
   [chicken-master.config :refer [settings]]
   [day8.re-frame.http-fx]
   [ajax.edn :as edn]

   ;; required for http mocks
   [chicken-master.backend-mocks :as mocks]))

(defn http-request [method endpoint & {:keys [params body on-success on-failure]
                                       :or {on-success ::process-fetched-days
                                            on-failure ::failed-blah}}]
  {:method method
   :uri (str (settings :backend-url) endpoint)
   :headers {"Content-Type" "application/edn"
             "authorization" "Basic c2lsb2E6a3JhY2g="}
   :format (edn/edn-request-format)
   :body (some-> body pr-str)
   :params params
   :response-format (edn/edn-response-format)
   :on-success  [on-success]
   :on-fail     [on-failure]})

(defn http-get [endpoint params on-success]
  (http-request :get endpoint :params params :on-success on-success))

(defn http-post [endpoint body]
  (http-request :post endpoint :body body))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   {:db db/default-db
    :fx [[:dispatch [::show-from-date (time/iso-date (time/today))]]
         [:dispatch [::fetch-stock]]
         [:dispatch [::fetch-orders]]]}))

(re-frame/reg-event-db ::hide-modal (fn [db [_ modal]] (assoc-in db [modal :show] nil)))

(re-frame/reg-event-fx
 ::confirm-action
 (fn [_ [_ msg on-confirm-event & params]]
   (when (js/confirm msg)
     {:fx [[:dispatch (into [on-confirm-event] params)]]})))

(re-frame/reg-event-fx
 ::remove-order
 (fn [_ [_ id]]
   {(settings :http-dispatch) (http-request :delete (str "orders/" id))}))

(re-frame/reg-event-fx
 ::move-order
 (fn [{{orders :orders start-date :start-date} :db} [_ id day]]
   {(settings :http-dispatch)
    (http-request :put (str "orders/" id)
                  :body (-> id orders (assoc :day day :start-from start-date)))}))

 (re-frame/reg-event-db
  ::edit-order
  (fn [{orders :orders :as db} [_ day id]]
    (assoc db :order-edit
           (-> orders
               (get id {:state :waiting})
               (merge {:show true :day day})))))

(re-frame/reg-event-fx
 ::fulfill-order
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:orders id :state] :pending)
    (settings :http-dispatch) (http-request :post (str "orders/" id "/fulfilled"))}))

(re-frame/reg-event-fx
 ::reset-order
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:orders id :state] :waiting)
    (settings :http-dispatch) (http-request :post (str "orders/" id "/waiting"))}))

(re-frame/reg-event-fx
 ::save-order
 (fn [{{order :order-edit} :db} [_ form]]
   {:dispatch [::hide-modal :order-edit]
    (settings :http-dispatch) (http-post (str "orders")
                                         (merge
                                          (select-keys order [:id :day :hour :state])
                                          (select-keys form [:id :day :hour :state :who :notes :products])))}))

(re-frame/reg-event-db
 ::process-fetched-days
 (fn [db [_ days]]
   (-> db
       (update :current-days #(map (fn [[day orders]]
                                     [day (if (contains? days day)
                                            (days day) orders)]) %))
       (update :orders #(reduce (fn [m cust] (assoc m (:id cust) cust)) % (-> days vals flatten))))))

(re-frame/reg-event-fx
 ::scroll-weeks
 (fn [{db :db} [_ offset]]
   {:fx [[:dispatch [::fetch-stock]]
         [:dispatch [::show-from-date (-> db
                                          :start-date
                                          time/parse-date
                                          (time/date-offset (* 7 offset))
                                          time/iso-date)]]]}))

(re-frame/reg-event-db
 ::show-from-date
 (fn [{:keys [start-date orders] :as db} [_ day]]
   (let [day (or day start-date)
         days (into #{} (time/get-weeks day 2))
         filtered-orders (->> orders vals (filter days))]
     (assoc db
            :start-date day
            :current-days (map #(vector % (get filtered-orders %)) days)))))

(re-frame/reg-event-fx
 ::fetch-orders
 (fn [_ [_ from to]]
   {(settings :http-dispatch) (http-get "orders" {} ::process-stock)}))

;; Customers events
(re-frame/reg-event-fx
 ::show-customers
 (fn [{db :db} _]
   {:db (assoc-in db [:clients :show] true)
    :dispatch [::fetch-stock]}))

(re-frame/reg-event-fx
 ::add-customer
 (fn [_ [_ customer-name]]
   {(settings :http-dispatch) (http-request :post "customers"
                                            :body {:name customer-name}
                                            :on-success ::process-stock)}))

;;; Storage events

(re-frame/reg-event-fx
 ::show-stock
 (fn [{db :db} _]
   {:db (assoc-in db [:stock :show] true)
    :dispatch [::fetch-stock]}))

(re-frame/reg-event-fx
 ::fetch-stock
 (fn [_ _]
   {(settings :http-dispatch) (http-get "stock" {} ::process-stock)}))

(defn assoc-if [coll key val] (if val (assoc coll key val) coll))
(re-frame/reg-event-fx
 ::process-stock
 (fn [{db :db} [_ {:keys [products customers orders]}]]
   (prn products customers orders)
   {:db (-> db
            (assoc-if :products products)
            (assoc-if :customers customers)
            (assoc-if :orders orders))
    :dispatch [::process-fetched-days (group-by :day (vals orders))]
    }))

(re-frame/reg-event-db
 ::update-product-stock
 (fn [db [_ product i]]
   (update-in db [:products product] + i)))

(re-frame/reg-event-db
 ::set-stock-amount
 (fn [db [_ product i]]
   (prn i)
   (assoc-in db [:products product] i)))

(re-frame/reg-event-db
 ::delete-product
 (fn [db [_ product]]
   (update db :products dissoc product)))

(re-frame/reg-event-fx
 ::save-stock
 (fn [_ [_ products]]
   {:dispatch [::hide-modal :stock]
    (settings :http-dispatch) (http-request :post "products" :body products :on-sucess ::process-stock)}))




(comment
  (re-frame/dispatch-sync [::show-stock])
  (re-frame/dispatch-sync [::update-product-stock :eggs 2])
  )
;;;;;;;; Backend mocks



(re-frame/reg-fx
 :http
 (fn [{:keys [method uri params body on-success on-fail]}]
   (condp = uri
     "http://localhost:3000/stock" (re-frame/dispatch (conj on-success (mocks/fetch-stock params)))

     "get-customers" (re-frame/dispatch (conj on-success (mocks/fetch-customers params)))
     "add-customer" (re-frame/dispatch (conj on-success (mocks/add-customer params)))
     "save-stock" (re-frame/dispatch (conj on-success (mocks/save-stocks body)))
     (let [parts (clojure.string/split uri "/")]
       (cond
         (and (= method :get) (= uri "http://localhost:3000/orders"))
         (re-frame/dispatch (conj on-success (mocks/fetch-orders params)))

         (and (= method :post) (= uri "http://localhost:3000/orders"))
         (re-frame/dispatch (conj on-success (mocks/replace-order nil (cljs.reader/read-string body))))

         (and (= method :post) (= uri "http://localhost:3000/products"))
         (re-frame/dispatch (conj on-success (mocks/save-stocks (cljs.reader/read-string body))))

         (= method :delete)
         (re-frame/dispatch (conj on-success (mocks/delete-order (-> parts (nth 4) (js/parseInt)))))


         (and (= method :post) (= uri "http://localhost:3000/customers"))
         (re-frame/dispatch (conj on-success (mocks/add-customer (cljs.reader/read-string body))))

         (-> parts last #{"fulfilled" "waiting"})
         (re-frame/dispatch (conj on-success (mocks/order-state {:id (-> parts (nth 4) (js/parseInt)) :state (keyword (last parts))})))
         true (prn "unhandled" method uri)
       ))
   )))
