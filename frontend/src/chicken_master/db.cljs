(ns chicken-master.db)

(def default-db
  {;; set automatically
   ;; :days {"2020-10-05" [1 2 3]
   ;;        "2020-10-06" [1 2 3]
   ;;        "2020-10-07" [1 2 3]
   ;;        "2020-10-08" [1 2 3]
   ;;        "2020-10-09" [1 2 3]
   ;;        "2020-10-10" [1 2 3]
   ;;        "2020-10-11" [1 2 3]
   ;;        "2020-10-12" [1 2 3]
   ;;        "2020-10-13" [1 2 3]
   ;;        "2020-10-14" [1 2 3]
   ;;        "2020-10-15" [1 2 3]
   ;;        "2020-10-16" [1 2 3]
   ;;        "2020-10-17" [1 2 3]
   ;;        "2020-10-18" [1 2 3]}
   ;; :order-edit {:show true
   ;;              :day "2020-10-10"}
   ;; :show-confirmation-modal {:show nil
   ;;                           :on-confirm-event :bla-bla-bla
   ;;                           :params [1 2 3]}


   ;; :current-days [{:day (google.date "2020-01-01") :orders []}]
   ;; :customers [{:id 1 :who "mr blobby"}]
   ;; :bearer-token "user-token"
   ::clients {:show nil} ; customers edit modal
   :stock {:show nil}
   :products {:eggs 22 :milk 32 :cabbage 54 :carrots 11 :cows 32 :ants 21}
   })
