(ns chicken-master.db)

(def default-db
  {;; set automatically
   ;; :customers {1 {:id 1 :who "mr.blobby (649 234 234)" :day "2020-10-10" :hour "02:12" :products {:eggs 2 :milk 3}}
   ;;             2 {:id 2 :who "da police (0118 999 881 999 119 725 123123 12 3123 123 )" :day "2020-10-10" :hour "02:12" :products {:eggs 12}}
   ;;             3 {:id 3 :who "johnny" :day "2020-10-10" :hour "02:12" :products {:eggs 5}}}
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
   :products {:eggs {}
              :milk {}
              :cabbage {}
              :carrots {}}
   })
