(ns chicken-master.products
  (:require
   [re-frame.core :as re-frame]
   [chicken-master.html :as html]
   [chicken-master.events :as event]))

(defn product-item [what amount available]
  [:div {:key (gensym)}
   [:div {:class :input-item}
    [:label {:for :product} "co"]
    [:select {:name :product :id :product :defaultValue what}
     [:option {:value nil} "-"]
     (for [product available]
       [:option {:key (gensym) :value product} (name product)])]]
   (html/input :amount "ile" {:type :number :default amount :min 0})])

(defn format-product [[product amount]]
  [:li {:key (gensym) :class :product}
   [:input {:class :product-amount :type :number :min 0 :defaultValue amount}]
   [:span {:class :product-name} product]])
