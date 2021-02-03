(ns chicken-master.products
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.config :refer [settings]]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn num-or-nil [val]
  (let [i (js/parseFloat val)]
    (when-not (js/isNaN i) i)))

(defn round [num digits]
  (let [div (js/Math.pow 10 digits)]
    (/ (js/Math.round (* num div)) div)))

(defn number-input [id label amount on-blur]
  (html/input id label
              {:type :number
               :default (round amount 3)
               :step :any
               :on-blur on-blur}))

(defn product-item [what amount available product-no]
  (let [id (gensym)]
    [:div {:class :product-item-edit :key (gensym)}
     [:div {:class :input-item}
      [:label {:for :product} "co"]
      [:select {:name (str "product-" id) :id :product :defaultValue what
                :on-change #(re-frame/dispatch [::event/selected-product (-> % .-target .-value) product-no])}
       [:option {:value ""} "-"]
       (for [[product _] available]
         [:option {:key (gensym) :value product} (name product)])]
      ]
     (number-input (str "amount-" id) "ile" amount
                   #(re-frame/dispatch [::event/changed-amount (-> % .-target .-value) product-no]))
     ]))

(defn format-product [[product amount]]
  [:div {:key (gensym) :class :product}
   [:span {:class :product-name} product]
   (if (settings :editable-number-inputs)
     (number-input (str "amount-" product) "" amount nil)
     [:span {:class :product-amount} amount])])
