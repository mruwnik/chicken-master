(ns chicken-master.products
  (:require
   [clojure.string :as str]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [chicken-master.html :as html]
   [chicken-master.subs :as subs]))

(defn num-or-nil [val]
  (let [i (js/parseFloat val)]
    (when-not (js/isNaN i) i)))

(defn round [num digits]
  (when num
    (let [div (js/Math.pow 10 digits)]
      (/ (js/Math.round (* num div)) div))))

(defn format-price [price] (when price (round (/ price 100) 2)))
(defn normalise-price [price] (when price (round (* price 100) 0)))

(defn number-input [id label amount on-blur]
  (html/input id label
              {:type :number
               :default (round amount 3)
               :step :any
               :on-focus #(set! (-> % .-target .-value) "")
               :on-blur on-blur}))

(defn collect-products [raw-values]
  (->> raw-values
       (remove (comp str/blank? second))
       (map (fn [[k v]] [(str/split k "-") v]))
       (group-by (comp last first))
       (map #(sort-by first (second %)))
       (map (partial reduce (fn [col [[k _] val]] (assoc col (keyword k) val)) {}))
       (filter :product)
       (map (fn [{:keys [product amount price]}]
              [product {:amount (num-or-nil amount)
                        :price (-> price num-or-nil normalise-price)}]))
       (remove (comp zero? :amount second))
       (group-by first)
       (map (fn [[product items]]
              [(keyword product) (->> items
                                      (map second)
                                      (map (partial reduce-kv #(if %3 (assoc %1 %2 %3) %1) {}))
                                      (apply merge-with +))]))
       (into {})))

(defn product-item [available state fields what]
  (let [id (gensym)]
    [:div {:class :product-item-edit :key (gensym)}
     [:div {:class :input-item}
      ;; [:label {:for :product} "co"]
      [:select {:name (str "product-" id) :id :product :defaultValue (or (some-> what name) "-")
                :on-change #(let [prod (-> % .-target .-value keyword)]
                              (if-not (= prod :-) (swap! state assoc prod {}))
                              (swap! state dissoc what))}
       (for [product (->> available (concat [what]) (remove nil?) sort vec)]
         [:option {:key (gensym) :value product} (name product)])
       [:option {:key (gensym) :value nil} "-"]]]
     (when (:amount fields)
       (number-input (str "amount-" id) nil (get-in @state [what :amount])
                     #(swap! state assoc-in [what :amount] (-> % .-target .-value num-or-nil))))
     (when (:price fields)
       [:div {:class :stock-product-price}
        (number-input (str "price-" id) "cena" (format-price (get-in @state [what :price]))
                      #(swap! state assoc-in
                              [what :price]
                              (some-> % .-target .-value num-or-nil normalise-price)))])]))

(defn products-edit [state & {:keys [available-prods getter-fn fields]
                              :or {available-prods @(re-frame/subscribe [::subs/available-products])
                                   fields #{:amount :price}}}]
  (let [all-product-names (-> available-prods keys set)]
    (swap! state #(or % {}))
    (fn []
      (let [available (remove (partial get @state) all-product-names)
            product-names (if (seq available)
                            (conj (->> @state (map first) vec) nil)
                            (map first @state))
            products (->> product-names
                          (map (partial product-item available state fields))
                          (into [:div {:class :product-items-edit}]))]
        (if getter-fn
          (conj products
                [:button {:type :button
                          :on-click #(getter-fn (dissoc @state nil))} "ok"])
          products)))))

(defn calc-price [who what price amount]
  (when-let [price (or price
                       (get-in @(re-frame/subscribe [::subs/customer-prices]) [who what])
                       (get-in @(re-frame/subscribe [::subs/available-products]) [what :price]))]
    (* amount price)))

(defn format-product [settings [product {:keys [amount final-price]}]]
  [:div {:key (gensym) :class :product}
   [:span {:class :product-name} product]
   (if (settings :editable-number-inputs)
     (number-input (str "amount-" product) "" amount nil)
     [:span {:class :product-amount} amount])
   (when (settings :prices)
     [:span {:class :product-price}
      (or (format-price final-price) (settings :empty-price-marker))])])

(defn item-adder [& {:keys [type value callback button class]
                     :or {type :text value "" button nil}}]
  (let [state (reagent/atom value)]
    (fn []
      [:div {:class class :on-click #(.stopPropagation %)}
       [:input {:type type :name :user-name :default-value value :value @state
                :on-change #(let [val (-> % .-target .-value)]
                              (reset! state val)
                              (if-not button (callback val)))}]
       (if button
         [:button {:class :add-product
                   :type :button
                   :disabled (= @state "")
                   :on-click (if callback #(-> state (reset-vals! "") first callback))} button])])))

(defn group-products [state]
  [:div {:class :input-item}
   [:label {:for :order-group-products} "stałe"]
   [:select {:class :order-group-products :id :order-group-products
             :value "-" :on-change #(some->> % .-target .-value
                                             (get (:group-products @state))
                                             (reset! (:products @state)))}
    [:option "-"]
    (for [[group _] (:group-products @state)]
      [:option {:key (gensym)} group])]])
