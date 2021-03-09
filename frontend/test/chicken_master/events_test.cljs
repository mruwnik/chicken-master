(ns chicken-master.events-test
  (:require
   [chicken-master.events :as sut]
   [chicken-master.subs :as subs]
   [cljs.test :refer-macros [deftest is testing]]
   [day8.re-frame.test :as rf-test]
   [re-frame.core :as rf]))


(defn set-db [updates]
  (rf/reg-event-db
   ::merge-db
   (fn [db [_ incoming]] (merge db incoming)))
  (rf/dispatch [::merge-db updates]))

(defn param-validator [event validator]
  (rf/reg-event-fx event (fn [_ [_ & params]] (validator params) nil)))


(deftest hide-modal
  (testing "models can be hidden"
    (rf-test/run-test-sync
     (set-db {:order-edit {:show true}
              :stock {:show true}
              :clients {:show true}
              :settings {:show true}})
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
     (is (nil? @(rf/subscribe [::subs/loading?])))
     (rf/dispatch [::sut/start-loading])
     (is @(rf/subscribe [::subs/loading?]))))

  (testing "loader gets cleared"
    (rf-test/run-test-sync
     (set-db {:loading? true})
     (is @(rf/subscribe [::subs/loading?]))
     (rf/dispatch [::sut/stop-loading])
     (is (nil? @(rf/subscribe [::subs/loading?]))))))

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
            {:show true :day "2020-01-01" :id 1}))))

  (testing "new orders can be edited"
    (rf-test/run-test-sync
     (set-db {:orders {1 {:id 1 :day "2012-12-12"}} :order-edit nil})

     (rf/dispatch [::sut/edit-order "2020-01-01" :new-order])

     (is (= @(rf/subscribe [::subs/editted-order])
            {:show true :day "2020-01-01" :state :waiting}))))

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

     (is (nil? @(rf/subscribe [::subs/show-edit-modal]))))))
