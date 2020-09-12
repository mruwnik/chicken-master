(ns chicken-master.css
  (:require [garden.def :refer [defstyles]]))

(defstyles screen
  [:html {:height "100%"}
   [:body {:height "100%"}
    [:.full-height {:height "100%"}]
    [:.popup {:position :fixed
              :height "100%"
              :width "100%"
              :overflow :auto
              :z-index 1
              :background-color "rgba(0,0,0,0.4)"}
     [:form {
             :background-color "#fefefe"
             :margin "15% auto"
             :padding "20px"
             :border "1px solid #888"
             :width "15%"
             }
      [:.input-item
       [:label {:min-width "60px"
                :display :inline-block}]]
      [:.form-buttons {:margin "10px"}
       [:* {:margin "20px"}]]]]

    [:.calendar {:display :grid
                 :grid-template-columns "14% 14% 14% 14% 14% 14% 14%"
                 :grid-template-rows "50% 50%"
                 }
     [:.day-header {:border "2px solid black"
                    :text-align :center
                    :font-size "30px"}]
     [:.day.today {:border "2px solid red"}]
     [:.day {:border "2px solid black"
             :overflow :auto}

      ; If each day has a header, then hide the rest of the border
      [:.day-header {:border "none"
                     :border-bottom "2px solid black"}]

      [:.orders {:padding-left "25px"}

       [:.actions {:display :none
                   :float :right}]
       [:.order:hover [:.actions {:display :inline}]]

       [:.who {:font-size "18px"
               :font-weight :bold
               :white-space :nowrap
               :overflow :hidden
               :text-overflow :ellipsis}]
       [:.products {:padding-left "25px"}
        [:.product {:margin-bottom "5px"}
         [:.product-amount {:width "40px"
                            :margin-right "10px"
                            :max-height "5px"}]]

        ]]]]]]
)
