(ns chicken-master.events
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.db :as db]
   [chicken-master.orders :as orders]
   [chicken-master.time :as time]))

(re-frame/reg-event-db ::initialize-db (fn [_ _] db/default-db))

(re-frame/reg-event-db ::hide-modal (fn [db _] (assoc db :order-edit {})))

(re-frame/reg-event-fx
 ::confirm-action
 (fn [_ [_ msg on-confirm-event & params]]
   (when (js/confirm msg)
     {:fx [[:dispatch (into [on-confirm-event] params)]]})))

(re-frame/reg-event-fx
 ::remove-order
 (fn [_ [_ id]]
   {:http {:method :delete
           :url    "delete-order"
           :params {:id id}
           :on-success  [::process-fetched-days]
           :on-fail     [::failed-blah]}}))

 (re-frame/reg-event-db
  ::edit-order
  (fn [{customers :customers :as db} [_ day id]]
    (assoc db :order-edit
           (-> customers
               (get id {:state :waiting})
               (update :products (comp vec (partial map (partial zipmap [:prod :amount]))))
               (merge {:show true :day day})))))

(re-frame/reg-event-fx
 ::fulfill-order
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:customers id :state] :pending)
    :fx [[:dispatch [::set-current-days]]]
    :http {:method :post
           :url    "fulfill-order"
           :params {:id id}
           :on-success  [::process-fetched-days]
           :on-fail     [::failed-blah]}}))

(re-frame/reg-event-fx
 ::reset-order
 (fn [{db :db} [_ id]]
   {:db (assoc-in db [:customers id :state] :waiting)
    :fx [[:dispatch [::set-current-days]]]
    :http {:method :post
           :url    "reset-order"
           :params {:id id}
           :on-success  [::process-fetched-days]
           :on-fail     [::failed-blah]}}))

(re-frame/reg-event-fx
 ::save-order
 (fn [{{order :order-edit} :db} [_ form]]
   {:fx [[:dispatch [::hide-modal]]]
    :http {:method :post
           :url    "save-order"
           :params (merge
                    (select-keys order [:id :day :hour :state])
                    (select-keys form [:who :notes :products]))
           :on-success  [::process-fetched-days]
           :on-fail     [::failed-blah]}}))

(re-frame/reg-event-db ::add-product (fn [db _] (update-in db [:order-edit :products] conj {})))
(re-frame/reg-event-db
 ::selected-product
 (fn [db [_ product product-no]]
   (assoc-in db [:order-edit :products product-no :prod] product)))
(re-frame/reg-event-db
 ::changed-amount
 (fn [db [_ amount product-no]]
   (assoc-in db [:order-edit :products product-no :amount] amount)))


(defn get-day [{:keys [days customers]} date]
  {:date date
   :customers (->> date
                   time/iso-date
                   (get days)
                   (map customers))})

(re-frame/reg-event-db
 ::set-current-days
 (fn [{start-day :start-date :as db} _]
   (->> start-day
        time/parse-date
        time/start-of-week
        (time/days-range 14)
        (map (partial get-day db))
        (assoc db :current-days))))

(re-frame/reg-event-fx
 ::process-fetched-days
 (fn [{db :db} [_ days]]
   (println "fetched days" days)
   {:db (-> db
            (update :days #(reduce-kv (fn [m k v] (assoc m k (map :id v))) % days))
            (update :customers #(reduce (fn [m cust] (assoc m (:id cust) cust)) % (-> days vals flatten))))
    :fx [[:dispatch [::set-current-days]]]}))


(defn missing-days
  "Return a map of missing days to be fetched."
  [db day]
  (let [missing-days (->> day
                          time/parse-date
                          time/start-of-week
                          (time/days-range 28)
                          (remove (comp (:days db {}) time/iso-date)))]
    (when (seq missing-days)
      {:from (->> missing-days (sort time/before?) first time/iso-date)
       :to   (->> missing-days (sort time/before?) last time/iso-date)})))

(re-frame/reg-event-fx
 ::scroll-weeks
 (fn [{db :db} [_ offset]]
   {:fx [[:dispatch [::show-from-date (-> db :start-date time/parse-date (time/date-offset (* 7 offset)))]]]}))

(re-frame/reg-event-fx
 ::show-from-date
 (fn [{db :db} [_ day]]
   (let [missing (missing-days db day)
         effects {:db (assoc db :start-date day)
                  :fx [[:dispatch [::set-current-days]]]}]
     (if-not missing
       effects
       (assoc effects :http {:method :get
                             :url    "get-days"
                             :params missing
                             :on-success  [::process-fetched-days]
                             :on-fail     [::failed-blah]})))))


(comment
  (re-frame/dispatch-sync [::show-from-date "2020-11-11"])
  )
;;;;;;;; Backend mocks

(re-frame/reg-fx
 :http
 (fn [{:keys [method url params on-success on-fail]}]
   (println params)
   (condp = url
     "get-days" (re-frame/dispatch (conj on-success (orders/fetch-days params)))
     "save-order" (re-frame/dispatch (conj on-success (orders/replace-order params)))
     "delete-order" (re-frame/dispatch (conj on-success (orders/delete-order params)))
     "fulfill-order" (re-frame/dispatch (conj on-success (orders/order-state (assoc params :state :fulfilled))))
     "reset-order" (re-frame/dispatch (conj on-success (orders/order-state (assoc params :state :waiting))))
   )))
