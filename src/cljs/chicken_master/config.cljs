(ns chicken-master.config
  (:require [re-frame.core :as re-frame]
            [chicken-master.time :as time]
            [chicken-master.subs :as subs]))

(def debug?
  ^boolean goog.DEBUG)

(def default-settings {:first-day-offset 1 ; which is the first day of the week (add the offset to `day-names`)
                       :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"] ; how days should be displayed in the calendar view
                       :calendar-heading false ; show a header with the names of days
                       :show-date true ; display the date for each day
                       :show-day-name-with-date true ; add the day name to each date
                       :show-day-add-order true ; Show an add order button in each day

                       :show-order-time false ; display the time of each order
                       :show-order-notes true ; display notes
                       :editable-number-inputs false ; only allow number modifications in the edit modal
                       :hide-fulfilled-orders false

                       :http-dispatch :http;-xhrio
                       :backend-url "http://localhost:3000/"
                       })

(defn- settings [key]
  (get @(re-frame/subscribe [::subs/settings]) key))


(re-frame/reg-event-db
 ::change-setting
 (fn [{settings :settings :as db} [_ key val]]
   (let [settings (assoc settings key val)]
     (time/update-settings settings)
     (assoc db :settings settings))))

(defn change-setting [key val]
  (re-frame/dispatch [::change-setting key val]))

(defn input [id label opts]
  (let [parser (or (:parser opts) identity)
        handlers (condp = (:type opts)
                   :checkbox {:defaultChecked (settings id)
                              :on-change #(change-setting id (-> % .-target .-checked parser))}
                   {:defaultValue (settings id)
                    :on-change #(change-setting id (-> % .-target .-value parser))})]
  [:div {:class :input-item}
   [:label {:for id} label]
   [:input (merge {:name id :id id} handlers (dissoc opts :parser))]]))

(defn settings-options []
  [:div
   [:h3 "Ustawienia wyglądu kalendarza"]
   (input :first-day-offset "o ile dni przesunąć niedziele w lewo"
          {:type :number :max 7 :min 0 :parser #(js/parseInt %)})
   (input :day-names "skróty nazw dni tygodnia"
          {:default (clojure.string/join ", " (settings :day-names))
           :parser #(clojure.string/split % #"\s*,\s*")})
   (input :calendar-heading "Pokaż nagłówek z dniami tygodnia" {:type :checkbox})

   [:h3 "Ustawienia wyglądu poszczególnych dni"]
   (input :show-date "Pokaż date" {:type :checkbox})
   (input :show-day-name-with-date "Pokaż nazwę dnia" {:type :checkbox})
   (input :show-day-add-order "Przycisk dodawania zamówienia" {:type :checkbox})

   [:h3 "Ustawienia wyglądu zamówien"]
   (input :show-order-time "pokaż czas zamówienia" {:type :checkbox})
   (input :show-order-notes "pokaż notki w zamówieniu" {:type :checkbox})
   (input :editable-number-inputs "możliwość bezposredniej edycji" {:type :checkbox})
   (input :hide-fulfilled-orders "ukryj wydane zamówienia" {:type :checkbox})

   [:h3 "Ustawienia tyłu"]
   [:label {:for :http-dispatch} "re-frame http dispatcher"]
   [:select {:id :http-dispatch :name :http-dispatch
             :on-change #(change-setting :http-dispatch (-> % .-target .-value keyword))}
    [:option {:value :http} "client side mock"]
    [:option {:value :http-xhrio} "re-frame-http-fx"]]

   (input :backend-url "backend URL" {})])
