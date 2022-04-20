(ns chicken-master.events-test
  (:require
   [chicken-master.events :as sut]
   [chicken-master.subs :as subs]
   [chicken-master.config :as config]
   [chicken-master.time :as time]
   [cljs.test :refer-macros [deftest is testing]]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as rf]))

(time/update-settings {:first-day-offset 1})

(defn set-db [updates]
  (rf/reg-event-db
   ::merge-db
   (fn [db [_ incoming]] (merge db incoming)))
  (rf/dispatch [::merge-db updates]))

(defn param-validator [event validator]
  (rf/reg-event-fx event (fn [_ [_ & params]] (validator params) nil)))

(def sample-orders
  [{:id 1 :day "2020-01-02" :state :waiting} {:id 2 :day "2020-01-02" :state :waiting} {:id 3 :day "2020-01-02" :state :waiting}
   {:id 4 :day "2020-01-04" :state :waiting}
   {:id 5 :day "2020-01-06" :state :waiting} {:id 6 :day "2020-01-06" :state :waiting}])

(def sample-orders-by-day (group-by :day sample-orders))
(def sample-orders-by-id (reduce #(assoc %1 (:id %2) (assoc %2 :days {(:day %2) (:state %2)})) {} sample-orders))

(deftest hide-modal
  (testing "models can be hidden"
    (rf-test/run-test-sync
     (set-db {:order-edit {:show true}
              :stock {:show true}
              :clients {:show true}
              :settings {:show true}
              :loading? 0})
     (is @(rf/subscribe [::subs/show-edit-modal]))
     (is @(rf/subscribe [::subs/show-stock-modal]))
     (is @(rf/subscribe [::subs/show-customers-modal]))
     (is @(rf/subscribe [::subs/show-settings-modal]))

     (rf/dispatch [::sut/hide-modal :order-edit])
     (is (nil? @(rf/subscribe [::subs/show-edit-modal])))

     (rf/dispatch [::sut/hide-modal :stock])
     (is (nil? @(rf/subscribe [::subs/show-stock-modal])))

     (rf/dispatch [::sut/hide-modal :clients])
     (is (nil? @(rf/subscribe [::subs/show-customers-modal])))

     (rf/dispatch [::sut/hide-modal :settings])
     (is (nil? @(rf/subscribe [::subs/show-settings-modal]))))))

(deftest loader-test
  (testing "loader gets set"
    (rf-test/run-test-sync
     (set-db {:loading? nil})
     (is (not @(rf/subscribe [::subs/loading?])))
     (rf/dispatch [::sut/start-loading])
     (is @(rf/subscribe [::subs/loading?]))))

  (testing "loader gets cleared"
    (rf-test/run-test-sync
     (set-db {:loading? true})
     (is @(rf/subscribe [::subs/loading?]))
     (rf/dispatch [::sut/stop-loading])
     (is (not @(rf/subscribe [::subs/loading?])))))

  (testing "multiple loads handled"
    (rf-test/run-test-sync
     (is (not @(rf/subscribe [::subs/loading?])))
     (rf/dispatch [::sut/start-loading])
     (rf/dispatch [::sut/start-loading])
     (rf/dispatch [::sut/start-loading])
     (is @(rf/subscribe [::subs/loading?]))

     (rf/dispatch [::sut/stop-loading])
     (is @(rf/subscribe [::subs/loading?]))

     (rf/dispatch [::sut/stop-loading])
     (rf/dispatch [::sut/stop-loading])
     (is (not @(rf/subscribe [::subs/loading?]))))))

(deftest confirm-action-test
  (testing "when confirmed, the provided event is called"
    (rf-test/run-test-sync
     (param-validator ::confirm #(is (= % [1 2])))

     (with-redefs [js/confirm (constantly true)]
       (rf/dispatch [::sut/confirm-action "bla bla" ::confirm 1 2]))))

  (testing "when not confirmed, nothing happens"
    (rf-test/run-test-sync
     (let [calls (atom [])]
       (param-validator ::confirm #(swap! calls conj %))
       ;; make sure that the action handler works by sending a test event
       (rf/dispatch [::confirm :check-call])

       (with-redefs [js/confirm (constantly false)]
         (rf/dispatch [::sut/confirm-action "bla bla" ::confirm 1 2]))
       (is (= @calls [[:check-call]]))))))

(deftest test-failed-request
  (testing "failed requests log errors"
    (rf-test/run-test-sync
     (set-db {:loading? true :current-user "mr blobby"})
     (param-validator ::sut/log-error #(is (= % ["{:status 500}"])))

     (is @(rf/subscribe [::subs/loading?]))
     (rf/dispatch [::sut/failed-request {:status 500}])

     (is (not @(rf/subscribe [::subs/loading?])))
     (is (= @(rf/subscribe [::subs/current-user]) "mr blobby"))))

  (testing "401s log the user out"
    (rf-test/run-test-sync
     (set-db {:loading? true :current-user "mr blobby"})
     (param-validator ::sut/log-error #(is (= % ["{:status 401}"])))

     (is @(rf/subscribe [::subs/loading?]))
     (rf/dispatch [::sut/failed-request {:status 401}])

     (is (not @(rf/subscribe [::subs/loading?])))
     (is (nil? @(rf/subscribe [::subs/current-user]))))))

(deftest test-orders-updates
  ;; FIXME: the request handler is not being overloaded
  (testing "orders get updated"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}}})
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :put))
                                    (is (= uri "orders/1"))
                                    (is (= body {:id 1 :day "2020-01-01"}))))

     (rf/dispatch [::sut/move-order 1 "2020-01-01"])))

  (testing "orders editor is shown"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}} :order-edit nil})

     (rf/dispatch [::sut/edit-order "2020-01-01" 1])

     (is (= @(rf/subscribe [::subs/editted-order])
            {:show true :day "2020-01-01" :id 1 :order-date "2020-01-01"}))))

  (testing "new orders can be edited"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}} :order-edit nil})

     (rf/dispatch [::sut/edit-order "2020-01-01" :new-order])

     (is (= @(rf/subscribe [::subs/editted-order])
            {:show true :day "2020-01-01" :state :waiting :order-date "2020-01-01"}))))

  ;; FIXME: the request handler is not being overloaded
  (testing "orders are fulfilled"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}} :order-edit nil})
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "orders/1/fulfilled"))
                                    (is (= body nil))))

     (rf/dispatch [::sut/fulfill-order 1])
     (is (= (-> [::subs/orders] rf/subscribe deref (get 1) :state) :pending))))

  ;; FIXME: the request handler is not being overloaded
  (testing "orders are reset"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}} :order-edit nil})
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "orders/1/waiting"))
                                    (is (= body nil))))

     (rf/dispatch [::sut/reset-order 1])
     (is (= (-> [::subs/orders] rf/subscribe deref (get 1) :state) :waiting))))

  ;; FIXME: the request handler is not being overloaded
  (testing "orders use the values in :order-edit if not provided"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2020-10-10" :hour "12" :state :waiting}}
              :order-edit {:show true}})
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "orders"))
                                    (is (= body {:id 1 :day "2020-10-10" :hour "12" :state :waiting :products {:eggs 2 :milk 3}}))))

     (rf/dispatch [::sut/save-order {:products {:eggs 2 :milk 3}}])

     (is (nil? @(rf/subscribe [::subs/show-edit-modal])))))

  ;; FIXME: the request handler is not being overloaded
  (testing "order edit forms overwrite previous values"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2020-10-10" :hour "12" :state :waiting}}
              :order-edit {:show true}})
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "orders"))
                                    (is (= body {:id 1 :day "2022-10-10" :hour "24"
                                                 :state :pending :note "asd" :who {:id 12 :name "mr blobby"}
                                                 :products {:eggs 2 :milk 3}}))))

     (rf/dispatch [::sut/save-order {:id 1 :day "2022-10-10" :hour "24"
                                     :state :pending :note "asd" :who {:id 12 :name "mr blobby"}
                                     :product {:eggs 2 :milk 3}}])

     (is (nil? @(rf/subscribe [::subs/show-edit-modal])))))

  ;; FIXME: the request handler is not being overloaded
  (testing "fetching orders works"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri]}]]
                                    (is (= method :get))
                                    (is (= uri "orders"))))
     (let [called (atom false)]
       (param-validator ::sut/start-loading #(reset! called true))
       (rf/dispatch [::sut/fetch-orders])

       (is @called)))))

(deftest test-process-fetched-days
  (testing "processing fetched days disables the loader"
    (rf-test/run-test-sync
     (set-db {:orders {} :current-days {} :loading? true})
     (rf/dispatch [::sut/process-fetched-days {}])

     (is (not @(rf/subscribe [::subs/loading?])))))

  (testing "orders get set correctly"
    (rf-test/run-test-sync
     (set-db {:orders {} :current-days {} :loading? true})
     (rf/dispatch [::sut/process-fetched-days sample-orders-by-day])

     (is (= @(rf/subscribe [::subs/orders]) sample-orders-by-id))))

  (testing "orders get updated correctly"
    (rf-test/run-test-sync
     (set-db {:current-days {} :loading? true
              :orders {1 {:id 1 :day "2020-01-01"}
                       2 {:id 2 :day "2020-01-01"}
                       3 {:id 3 :day "2020-01-01"}
                       14 {:id 14 :day "2020-01-14"}
                       15 {:id 15 :day "2020-01-16"}}})
     (rf/dispatch [::sut/process-fetched-days sample-orders-by-day])

     (is (= @(rf/subscribe [::subs/orders])
            (merge sample-orders-by-id
                   {14 {:id 14 :day "2020-01-14"}
                    15 {:id 15 :day "2020-01-16"}})))))

  (testing "current days don't get overwitten"
    (rf-test/run-test-sync
     (set-db {:orders {} :current-days [] :loading? true})
     (rf/dispatch [::sut/process-fetched-days sample-orders-by-day])

     (is (= @(rf/subscribe [::subs/current-days]) []))))

  (testing "current days get updated correctly"
    (rf-test/run-test-sync
     (set-db {:orders {} :loading? true
              :current-days [["2020-01-01" [{:id :left-as-is :day "2020-01-01"}]]
                             ["2020-01-02" [{:id :will-be-replaced :day "2020-01-02"}]]
                             ["2020-01-03" nil]
                             ["2020-01-04" nil]
                             ["2020-01-05" nil]
                             ["2020-01-06" [{:id :replaced :day "2020-01-06"}]]
                             ["2020-01-07" nil]]})
     (rf/dispatch [::sut/process-fetched-days sample-orders-by-day])

     (is (= @(rf/subscribe [::subs/current-days])
            [["2020-01-01" [{:id :left-as-is :day "2020-01-01"}]]
             ["2020-01-02" [{:id 1 :day "2020-01-02" :state :waiting} {:id 2 :day "2020-01-02" :state :waiting} {:id 3 :day "2020-01-02" :state :waiting}]]
             ["2020-01-03" nil]
             ["2020-01-04" [{:id 4 :day "2020-01-04" :state :waiting}]]
             ["2020-01-05" nil]
             ["2020-01-06" [{:id 5 :day "2020-01-06" :state :waiting} {:id 6 :day "2020-01-06" :state :waiting}]]
             ["2020-01-07" nil]])))))

(deftest test-show-from-date
  (testing "showing days disables the loader"
    (rf-test/run-test-sync
     (set-db {:orders {} :current-days {} :loading? true})
     (rf/dispatch [::sut/show-from-date "2020-01-01"])

     (is (not @(rf/subscribe [::subs/loading?])))))

  (testing "showing from date works"
    (rf-test/run-test-sync
     (set-db {:orders sample-orders-by-id :current-days [] :loading? true})
     (rf/dispatch [::sut/show-from-date "2019-12-30"])

     (is (= @(rf/subscribe [::subs/current-days])
            [["2019-12-30" nil]
             ["2019-12-31" nil]
             ["2020-01-01" nil]
             ["2020-01-02" [{:id 1, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 2, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 3, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}]]
             ["2020-01-03" nil]
             ["2020-01-04" [{:id 4, :day "2020-01-04" :state :waiting, :days {"2020-01-04" :waiting}}]]
             ["2020-01-05" nil]
             ["2020-01-06" [{:id 5, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}
                            {:id 6, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}]]
             ["2020-01-07" nil] ["2020-01-08" nil] ["2020-01-09" nil]
             ["2020-01-10" nil] ["2020-01-11" nil] ["2020-01-12" nil]]))))

  (testing "showing from date starts from the beginning of the week"
    (rf-test/run-test-sync
     (set-db {:orders sample-orders-by-id :current-days [] :loading? true})
     (rf/dispatch [::sut/show-from-date "2020-01-03"])

     (is (= @(rf/subscribe [::subs/current-days])
            [["2019-12-30" nil]
             ["2019-12-31" nil]
             ["2020-01-01" nil]
             ["2020-01-02" [{:id 1, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 2, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 3, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}]]
             ["2020-01-03" nil]
             ["2020-01-04" [{:id 4, :day "2020-01-04" :state :waiting, :days {"2020-01-04" :waiting}}]]
             ["2020-01-05" nil]
             ["2020-01-06" [{:id 5, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}
                            {:id 6, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}]]
             ["2020-01-07" nil] ["2020-01-08" nil] ["2020-01-09" nil]
             ["2020-01-10" nil] ["2020-01-11" nil] ["2020-01-12" nil]]))))

  (testing "showing from date uses value from db if none provided"
    (rf-test/run-test-sync
     (set-db {:orders sample-orders-by-id :start-date "2020-01-03"})
     (rf/dispatch [::sut/show-from-date])

     (is (= @(rf/subscribe [::subs/current-days])
            [["2019-12-30" nil]
             ["2019-12-31" nil]
             ["2020-01-01" nil]
             ["2020-01-02" [{:id 1, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 2, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}
                            {:id 3, :day "2020-01-02" :state :waiting, :days {"2020-01-02" :waiting}}]]
             ["2020-01-03" nil]
             ["2020-01-04" [{:id 4, :day "2020-01-04" :state :waiting, :days {"2020-01-04" :waiting}}]]
             ["2020-01-05" nil]
             ["2020-01-06" [{:id 5, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}
                            {:id 6, :day "2020-01-06" :state :waiting, :days {"2020-01-06" :waiting}}]]
             ["2020-01-07" nil] ["2020-01-08" nil] ["2020-01-09" nil]
             ["2020-01-10" nil] ["2020-01-11" nil] ["2020-01-12" nil]])))))

(deftest test-customers
  (testing "customers are fetched before showing"
    (rf-test/run-test-sync
     (set-db {:clients {}})
     (let [called (atom false)]
       (param-validator ::sut/fetch-stock #(reset! called true))
       (rf/dispatch [::sut/show-customers])

       (is @(rf/subscribe [::subs/show-customers-modal]))
       (is @called))))

  ;; FIXME: the request handler is not being overloaded
  (testing "customers can be added"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "customers"))
                                    (is (= body {:name "mr blobby"}))))

     (rf/dispatch [::sut/add-customer "mr blobby"])))

  ;; FIXME: the request handler is not being overloaded
  (testing "customers can be removed"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri]}]]
                                    (is (= method :delete))
                                    (is (= uri "customers/1"))))

     (rf/dispatch [::sut/remove-customer 1])))

  ;; FIXME: the request handler is not being overloaded
  (testing "product groups can be saved"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "customers/1/product-group"))
                                    (is (= body {:name "bla" :products {:eggs 1 :milk 3}}))))

     (rf/dispatch [::sut/save-product-group 1 {:name "bla" :products {:eggs 1 :milk 3}}]))))


(deftest stock-tests
  (testing "stock fetched before showing"
    (rf-test/run-test-sync
     (set-db {:stock {} :loading? 0})
     (let [called (atom false)]
       (param-validator ::sut/fetch-stock #(reset! called true))
       (rf/dispatch [::sut/show-stock])

       (is @(rf/subscribe [::subs/show-stock-modal]))
       (is @called))))

  ;; FIXME: the request handler is not being overloaded
  (testing "stock gets fetched"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri]}]]
                                    (is (= method :get))
                                    (is (= uri "stock"))))

     (let [called (atom false)]
       (param-validator ::sut/start-loading #(reset! called true))

       (rf/dispatch [::sut/fetch-stock])

       (is @called))))

  (testing "the loader is hidden after stock gets processed"
    (rf-test/run-test-sync
     (let [called (atom false)]
       (param-validator ::sut/stop-loading #(reset! called true))

       (rf/dispatch [::sut/process-stock {}])

       (is @called))))

  (testing "stock gets processed"
    (rf-test/run-test-sync
     (rf/dispatch [::sut/process-stock {:products {:eggs 1 :milk 2}
                                        :customers [{:id 1 :who "mr blobby"}
                                                    {:id 2 :who "johhny"}]}])

     (is (= @(rf/subscribe [::subs/available-products]) {:eggs 1 :milk 2}))
     (is (= @(rf/subscribe [::subs/available-customers])
            [{:id 1 :who "mr blobby"} {:id 2 :who "johhny"}]))))

  (testing "stock isn't replaced if not in items"
    (rf-test/run-test-sync
     (set-db {:customers [{:id 1 :who "mr blobby"} {:id 2 :who "johhny X"}]})
     (rf/dispatch [::sut/process-stock {:products {:eggs 1 :milk 2}}])

     (is (= @(rf/subscribe [::subs/available-products]) {:eggs 1 :milk 2}))
     (is (= @(rf/subscribe [::subs/available-customers])
            [{:id 1 :who "mr blobby"} {:id 2 :who "johhny X"}]))))

  (testing "stock is replaced if in items but empty"
    (rf-test/run-test-sync
     (set-db {:customers [{:id 1 :who "mr blobby"} {:id 2 :who "johhny X"}]})
     (rf/dispatch [::sut/process-stock {:products {:eggs 1 :milk 2} :customers nil}])

     (is (= @(rf/subscribe [::subs/available-products]) {:eggs 1 :milk 2}))
     (is (= @(rf/subscribe [::subs/available-customers]) nil))))

  ;; FIXME: the request handler is not being overloaded
  (testing "stock gets saved"
    (rf-test/run-test-sync
     (param-validator :http-xhrio (fn [[{:keys [method uri body]}]]
                                    (is (= method :post))
                                    (is (= uri "products"))
                                    (is (= body {:eggs 1 :milk 2}))))
     (let [hidden? (atom false)
           loading? (atom false)]
       (param-validator ::sut/hide-modal #(reset! hidden? true))
       (param-validator ::sut/start-loading #(reset! loading? true))

       (rf/dispatch [::sut/save-stock {:eggs 1 :milk 2}])

       (is @hidden?)
       (is @loading?)))))


(deftest test-settings
  (testing "settings get shown"
    (rf-test/run-test-sync
     (set-db {:settings {} :loading? 0})
     (rf/dispatch [::sut/show-settings])
     (is @(rf/subscribe [::subs/show-settings-modal]))))

  (testing "users can be set"
    (rf-test/run-test-sync
     (let [loading? (atom false)
           token "bXIgYmxvYmJ5OmJsYQ=="]
       (with-redefs [config/set-item! #(is (= %2 token))]
         (set-db {:current-user nil})
         (param-validator ::sut/load-db #(reset! loading? true))

         (rf/dispatch [::sut/set-user {"name" "mr blobby" "password" "bla"}])

         (is @loading?)
         (is (= @(rf/subscribe [::subs/current-user]) token)))))))
