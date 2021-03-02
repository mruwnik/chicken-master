(ns chicken-master.events
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.db :as db]
   [chicken-master.time :as time]
   [chicken-master.config :refer [settings default-settings set-item!]]
   [day8.re-frame.http-fx]
   [ajax.edn :as edn]
   [goog.crypt.base64 :as b64]))

(defn http-request [method endpoint & {:keys [params body on-success on-failure]
                                       :or {on-success ::process-fetched-days
                                            on-failure ::failed-request}}]
  {:method method
   :uri (str (settings :backend-url) endpoint)
   :headers {"Content-Type" "application/edn"
             "authorization" (str "Basic " (some-> js/window (.-localStorage) (.getItem :bearer-token)))}
   :format (edn/edn-request-format)
   :body (some-> body pr-str)
   :params params
   :response-format (edn/edn-response-format)
   :on-success  [on-success]
   :on-failure     [on-failure]})


(defn http-get [endpoint params on-success]
  (http-request :get endpoint :params params :on-success on-success))

(defn http-post [endpoint body]
  (http-request :post endpoint :body body))

(re-frame/reg-event-fx
 ::initialize-db
 (fn [_ _]
   (time/update-settings default-settings)
   (let [user (some-> js/window (.-localStorage) (.getItem :bearer-token))]
     {:db (assoc db/default-db
                 :settings default-settings
                 :current-user user)
      :dispatch [(when user ::load-db)]})))

(re-frame/reg-event-fx
 ::load-db
 (fn [_ _]
   (time/update-settings default-settings)
   {:fx [[:dispatch [::show-from-date (time/iso-date (time/today))]]
         [:dispatch [::start-loading]]
         [:dispatch [::fetch-stock]]
         [:dispatch [::fetch-orders]]]}))

(re-frame/reg-event-db ::hide-modal (fn [db [_ modal]] (assoc-in db [modal :show] nil)))
(re-frame/reg-event-db ::start-loading (fn [db _] (assoc db :loading? true)))
(re-frame/reg-event-db ::stop-loading (fn [db _] (assoc db :loading? nil)))

(re-frame/reg-event-fx
 ::confirm-action
 (fn [_ [_ msg on-confirm-event & params]]
   (when (js/confirm msg)
     {:fx [[:dispatch (into [on-confirm-event] params)]]})))

(re-frame/reg-event-db
 ::failed-request
 (fn [db [_ response]]
   (.error js/console (str response))
   (js/alert "Wystąpił błąd")
   (-> db
       (assoc :loading? false)
       (update :current-user #(when-not (= (:status response) 401) %)))))

(re-frame/reg-event-fx
 ::remove-order
 (fn [_ [_ id]]
   {:http-xhrio (http-request :delete (str "orders/" id))}))

(re-frame/reg-event-fx
 ::move-order
 (fn [{{orders :orders start-date :start-date} :db} [_ id day]]
   {:http-xhrio
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
    :http-xhrio (http-request :post (str "orders/" id "/fulfilled"))}))

(re-frame/reg-event-fx
 ::reset-order
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:orders id :state] :waiting)
    :http-xhrio (http-request :post (str "orders/" id "/waiting"))}))

(re-frame/reg-event-fx
 ::save-order
 (fn [{{order :order-edit} :db} [_ form]]
   {:dispatch [::hide-modal :order-edit]
    :http-xhrio (http-post (str "orders")
                           (merge
                            (select-keys order [:id :day :hour :state])
                            (select-keys form [:id :day :hour :state :who :notes :products])))}))

(re-frame/reg-event-db
 ::process-fetched-days
 (fn [db [_ days]]
   (-> db
       (assoc :loading? nil)
       (update :current-days (fn [current-days]
                               (for [[day orders] current-days]
                                 [day (if (contains? days day) (days day) orders)])))
       (update :orders #(reduce (fn [m order] (assoc m (:id order) order)) % (mapcat second days))))))

(re-frame/reg-event-fx
 ::scroll-weeks
 (fn [{db :db} [_ offset]]
   {:fx [[:dispatch [::start-loading]]
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
         filtered-orders (->> orders vals (filter (comp days :day)) (group-by :day))]
     (assoc db
            :loading? nil
            :start-date day
            :current-days (map #(vector % (get filtered-orders %)) (sort days))))))

(re-frame/reg-event-fx
 ::fetch-orders
 (fn [_ [_ from to]]
   {:dispatch [::start-loading]
    :http-xhrio (http-request :get "orders")}))

;; Customers events
(re-frame/reg-event-fx
 ::show-customers
 (fn [{db :db} _]
   {:db (assoc-in db [:clients :show] true)
    :dispatch [::fetch-stock]}))

(re-frame/reg-event-fx
 ::add-customer
 (fn [_ [_ customer-name]]
   {:http-xhrio (http-request :post "customers"
                                            :body {:name customer-name}
                                            :on-success ::process-stock)}))
(re-frame/reg-event-fx
 ::remove-customer
 (fn [_ [_ id]]
   {:dispatch [::start-loading]
     :http-xhrio (http-request :delete (str "customers/" id)
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
   {:dispatch [::start-loading]
    :http-xhrio (http-get "stock" {} ::process-stock)}))

(defn assoc-if [coll key val] (if val (assoc coll key val) coll))
(re-frame/reg-event-fx
 ::process-stock
 (fn [{db :db} [_ {:keys [products customers]}]]
   {:db (-> db
            (assoc-if :products products)
            (assoc-if :customers customers))
    :dispatch [::stop-loading]
    }))

(re-frame/reg-event-fx
 ::save-stock
 (fn [_ [_ products]]
   {:fx [[:dispatch [::hide-modal :stock]]
         [:dispatch [::start-loading]]]
    :http-xhrio (http-request :post "products" :body products :on-success ::process-stock)}))

;; Settings

(re-frame/reg-event-db
 ::show-settings
 (fn [db _]
   (assoc-in db [:settings :show] true)))


(re-frame/reg-event-fx
 ::set-user
 (fn [{db :db} [_ user]]
   (set-item! :bearer-token (b64/encodeString (str (user "name") ":" (user "password"))))
   {:db (assoc db :current-user (b64/encodeString (str (user "name") ":" (user "password"))))
    :dispatch [::load-db]}))


(comment
  (re-frame/dispatch-sync [::show-stock])
  (re-frame/dispatch-sync [::update-product-stock :eggs 2])
  )
