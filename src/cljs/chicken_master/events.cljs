(ns chicken-master.events
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.db :as db]
   [chicken-master.time :as time]))

(re-frame/reg-event-db ::initialize-db (fn [_ _] db/default-db))

(re-frame/reg-event-db ::hide-modal (fn [db _] (assoc db :order-edit {})))
(re-frame/reg-event-db
 ::edit-order
 (fn [{customers :customers :as db} [_ day id]]
   (assoc db :order-edit
          (-> customers
              (get id)
              (update :products (comp vec (partial map (partial zipmap [:prod :amount]))))
              (merge {:show true :day day})))))

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
 ::show-from-date
 (fn [db [_ date]]
   (->> date
        time/start-of-week
        (time/days-range 14)
        (map (partial get-day db))
        (assoc db :current-days))))
