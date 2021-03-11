(ns chicken-master.config
  (:require [re-frame.core :as re-frame]
            [chicken-master.time :as time]
            [chicken-master.subs :as subs]
            [cljs.reader :refer [read-string]]))

(def debug?
  ^boolean goog.DEBUG)

(defn set-item!
  "Set `key' in browser's localStorage to `val`."
  [key val]
  (.setItem (.-localStorage js/window) key val))

(defn get-setting
  "Returns value of `key' from browser's localStorage."
  ([key]
   (-> js/window (.-localStorage) (.getItem :settings) read-string (get key)))
  ([key default]
   (if (nil? (get-setting key))
     default
     (get-setting key))))

(def default-settings {:first-day-offset (get-setting :first-day-offset 1) ; which is the first day of the week (add the offset to `day-names`)
                       :day-names (get-setting :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"]) ; how days should be displayed in the calendar view
                       :date-format (get-setting :date-format "%D %m/%d") ; the format of the days (D - name, d - day, m - month)

                       :show-day-add-order (get-setting :show-day-add-order true) ; Show an add order button in each day

                       :show-order-time (get-setting :show-order-time false) ; display the time of each order
                       :show-order-notes (get-setting :show-order-notes true) ; display notes
                       :editable-number-inputs (get-setting :editable-number-inputs false) ; only allow number modifications in the edit modal
                       :hide-fulfilled-orders (get-setting :hide-fulfilled-orders false)

                       :backend-url (get-setting :backend-url
                                                 (if (= (.. js/window -location -href) "http://localhost:8280/")
                                                   "http://localhost:3000/api/"
                                                   (str (.. js/window -location -href) "api/")))
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
  (prn key val)
  (set-item! :settings (assoc (get-setting :settings) key val))
  (re-frame/dispatch [::change-setting key val]))

(defn input [id label opts]
  (let [parser (or (:parser opts) identity)
        handlers (condp = (:type opts)
                   :checkbox {:defaultChecked (settings id)
                              :on-change #(change-setting id (-> % .-target .-checked parser))}
                   :radio {:defaultChecked (settings id)
                           :on-click #(change-setting id (-> % .-target .-value parser))}
                   {:defaultValue (settings id)
                    :on-change #(change-setting id (-> % .-target .-value parser))})]
  [:div {:class :input-item}
   (when (and label (not (#{:checkbox :radio} (:type opts))))
     [:label {:for id} label])
   [:input (merge {:name id :id id} handlers (dissoc opts :parser))]
   (when (and label (#{:checkbox :radio} (:type opts)))
     [:label {:for id} label])
   ]))

(defn settings-options []
  [:div
   [:h3 "Ustawienia wyglądu kalendarza"]
   [:button {:type :button :on-click #(set-item! :bearer-token nil)} "wyloguj"]

   (input :first-day-offset "o ile dni przesunąć niedziele w lewo"
          {:type :number :max 7 :min 0 :parser #(js/parseInt %)})
   (input :day-names "Nazwy dni tygodnia"
          {:default (clojure.string/join ", " (settings :day-names))
           :parser #(clojure.string/split % #"\s*,\s*")})
   (input :calendar-heading "Pokaż nagłówek z dniami tygodnia" {:type :checkbox})

   [:h3 "Ustawienia wyglądu poszczególnych dni"]
    (input :date-format "Format daty. %D wstawia nazwę dnia, %d dzień a %m miesiąc" {})

   (input :show-day-add-order "Przycisk dodawania zamówienia" {:type :checkbox})

   [:h3 "Ustawienia wyglądu zamówien"]
   (input :show-order-time "pokaż czas zamówienia" {:type :checkbox})
   (input :show-order-notes "pokaż notki w zamówieniu" {:type :checkbox})
   (input :editable-number-inputs "możliwość bezposredniej edycji" {:type :checkbox})
   (input :hide-fulfilled-orders "ukryj wydane zamówienia" {:type :checkbox})

   [:h3 "Ustawienia tyłu"]
   (input :backend-url "backend URL" {})
   ])
