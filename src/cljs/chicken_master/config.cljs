(ns chicken-master.config)

(def debug?
  ^boolean goog.DEBUG)

(def settings {:first-day-offset 1 ; which is the first day of the week (add the offset to `day-names`)
               :day-names ["Niedz" "Pon" "Wt" "Śr" "Czw" "Pt" "Sob"] ; how day should be displayed in the calendar view
               :calendar-heading false ; show a header with the names of days
               :show-date true ; display the date for each day
               :show-day-name-with-date true ; add the day name to each date
               :show-day-add-order false ; Show an add order button in each day

               :show-order-time false ; display the time of each order
               :show-order-notes true ; display notes
               :editable-number-inputs false ; only allow number modifications in the edit modal
               :hide-fulfilled-orders false

               :http-dispatch :http;-xhrio
               :backend-url "http://localhost:3000/"
               })
