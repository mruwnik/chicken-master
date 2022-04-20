(ns chicken-master.orders-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [next.jdbc.types :as jdbc.types]
   [chicken-master.orders :as sut]
   [chicken-master.products :as products]
   [chicken-master.time :as t]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(defn raw-order-row [& {:keys [id notes status date user_id user_name products recurrence]
                        :or {id 1 notes "note" status "pending" date #inst "2020-01-01"
                             user_id 2 user_name "mr blobby" recurrence nil
                             products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}}]
  (if products
    (for [[product {:keys [amount price]}] products]
      (merge #:orders{:id id :notes notes :order_date date :end_date date :recurrence recurrence}
             #:recurrence_exceptions{:order_id id :order_date date :status status}
             #:customers{:id user_id :name user_name}
             {:products/name (name product) :order_products/price price :order_products/amount amount}))
    [(merge #:orders{:id id :notes notes :order_date date :end_date date :recurrence recurrence}
            #:recurrence_exceptions{:order_id id :order_date date :status status}
            #:customers{:id user_id :name user_name}
            {:products/name nil :order_products/price nil :order_products/amount nil})]))

(deftest structure-order-test
  (testing "basic structure"
    (is (= (sut/structure-order (raw-order-row))
           {:id 1, :notes "note", :recurrence nil,
            :who {:id 2, :name "mr blobby"},
            :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}})))

  (testing "missing products"
    (is (= (sut/structure-order (raw-order-row :products nil))
           {:id 1, :notes "note", :recurrence nil
            :who {:id 2, :name "mr blobby"},
            :products {}}))))

(deftest test-get-order
  (testing "correct values returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.end_date >= ? AND o.order_date <= ?  AND o.id = ? AND o.user_id = ?"))
                              (is (= params [(t/to-db-date t/min-date) (t/to-db-date t/max-date) 123 "1"]))
                              (raw-order-row))]
      (is (= (sut/get-order :tx "1" 123)
             {:id 1, :notes "note", :recurrence nil :state :pending, :day "2020-01-01",
              :who {:id 2, :name "mr blobby"},
              :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}))))

  (testing "Only 1 item returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.end_date >= ? AND o.order_date <= ?  AND o.id = ? AND o.user_id = ?"))
                              (is (= params [(t/to-db-date t/min-date) (t/to-db-date t/max-date) 123 "1"]))
                              (concat (raw-order-row)
                                      (raw-order-row :id 21)))]
      (is (= (sut/get-order :tx "1" 123)
             {:id 1, :notes "note", :state :pending, :day "2020-01-01",
              :who {:id 2, :name "mr blobby"}, :recurrence nil
              :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}})))))

(deftest test-get-all
  (testing "correct values returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.end_date >= ? AND o.order_date <= ?  AND o.user_id = ?"))
                              (is (= params [(t/to-db-date t/min-date) (t/to-db-date t/max-date) "1"]))
                              (concat
                               (raw-order-row :id 1 :status "waiting")
                               (raw-order-row :id 2 :date #inst "2020-01-03")
                               (raw-order-row :id 3 :user_id 43 :user_name "John")
                               (raw-order-row :id 4)))]
      (is (= (sut/get-all "1")
             {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                             :who {:id 2, :name "mr blobby"}, :recurrence nil
                             :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}
                            {:id 3, :notes "note", :state :pending, :day "2020-01-01",
                             :who {:id 43, :name "John"}, :recurrence nil
                             :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}
                            {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                             :who {:id 2, :name "mr blobby"}, :recurrence nil
                             :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]
              "2020-01-03" [{:id 2, :notes "note", :state :pending, :day "2020-01-03",
                             :who {:id 2, :name "mr blobby"}, :recurrence nil
                             :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]})))))

(deftest test-replace!
  (testing "basic replace order"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs {:amount 12 :price 43} :milk {:amount 3 :price nil}}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly nil)
                    products/products-map (constantly {"eggs" 1 "milk" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount :price]))
                                        (is (= values [[1 1 12 43] [1 2 3 nil]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting")
                                           (raw-order-row :id 4)))]
    (is (= (sut/replace! :user-id order)
           {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"}, :recurrence nil
                           :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}
                          {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"}, :recurrence nil
                           :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]})))))

  (testing "replace order from different day"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-02",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs {:amount 12 :price 65} :milk {:amount 3 :price nil}}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01"})
                    products/products-map (constantly {"eggs" 1 "milk" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount :price]))
                                        (is (= values [[1 1 12 65] [1 2 3 nil]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting" :date #inst "2020-01-02")
                                           (raw-order-row :id 4)))]
        (is (= {"2020-01-02" [{:id 1, :notes "note", :recurrence nil,
                               :who {:id 2, :name "mr blobby"},
                               :day "2020-01-02", :state :waiting
                               :products {:eggs {:amount 12, :price nil}, :milk {:amount 3, :price 423}},}
                              {:id 4, :notes "note", :recurrence nil,
                               :who {:id 2, :name "mr blobby"},
                               :day "2020-01-02", :state :waiting
                               :products {:eggs {:amount 12, :price nil}, :milk {:amount 3, :price 423}}}]}
           (sut/replace! :user-id order))))))

  (testing "unknown products are ignored"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs {:amount 12 :price 89} :milk {:amount 3 :price nil}}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly nil)
                    products/products-map (constantly {"eggs" 1 "candles" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount :price]))
                                        (is (= values [[1 1 12 89]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting")
                                           (raw-order-row :id 4)))]
    (is (= (sut/replace! :user-id order)
           {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"}, :recurrence nil
                           :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}
                          {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"}, :recurrence nil
                           :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))))))

(deftest test-delete!
  (testing "non deleted items from day are returned"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01"})
                  sql/delete! (fn [_ table by]
                                (is (= table :orders))
                                (is (= by {:id 1 :user_id :user-id})))
                  sql/query (constantly (raw-order-row :id 4))]
    (is (= (sut/delete! :user-id nil nil 1)
           {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"}, :recurrence nil
                           :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))))

  (testing "nothing returned if no date set for the given order"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute-one! (constantly nil)
                  sql/delete! (fn [_ table by]
                                (is (= table :orders))
                                (is (= by {:id 1 :user_id :user-id})))
                  sql/query (constantly (raw-order-row :id 4))]
      (is (nil? (sut/delete! :user-id nil nil 1)))))

  (testing "non recurrence items"
    (let [invocations (atom [])]
      (with-redefs [jdbc.types/as-other identity
                    jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01"})
                    sql/delete! (fn [_ table by]
                                  (swap! invocations conj ["deleting" table by]))
                    sql/query (constantly (raw-order-row :id 4))
                    sql/update! (fn [_ table status key] (swap! invocations conj ["updating" table status key]))
                    sql/insert! (fn [_ table values] (swap! invocations conj ["inserting" table values]))]
        (testing "deleting without provided a date will remove the whole order"
          (reset! invocations [])
          (is (= (sut/delete! :user-id nil nil 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence nil
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
          (is (= [["deleting" :orders {:id 1 :user_id :user-id}]]
                 @invocations)))

        (testing "a provided date is ignored and will full delete"
          (reset! invocations [])
          (is (= (sut/delete! :user-id "2020-01-01" nil 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence nil
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
          (is (= [["deleting" :orders {:id 1 :user_id :user-id}]]
                 @invocations)))

        (testing "action-type is ignored and will full delete"
          (with-redefs [jdbc/execute-one! (fn [_ [q]]
                                            (when-not (str/includes? q "recurrence_exceptions")
                                              {:orders/order_date #inst "2020-01-01"}))]

            (reset! invocations [])
          (is (= (sut/delete! :user-id "2020-01-01" :single 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence nil
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
            (is (= [["deleting" :orders {:id 1 :user_id :user-id}]]
                   @invocations)))))))

  (testing "recurrence items"
    (let [invocations (atom [])]
      (with-redefs [jdbc.types/as-other identity
                    jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01" :orders/recurrence "FREQ=DAILY;COUNT=1"})
                    sql/delete! (fn [_ table by]
                                  (swap! invocations conj ["deleting" table by]))
                    sql/query (constantly (raw-order-row :id 4 :recurrence "FREQ=DAILY;COUNT=1"))
                    sql/update! (fn [_ table status key] (swap! invocations conj ["updating" table status key]))
                    sql/insert! (fn [_ table values] (swap! invocations conj ["inserting" table values]))]
        (testing "deleting with :all remove the whole order"
          (reset! invocations [])
          (is (= (sut/delete! :user-id nil :all 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence "FREQ=DAILY;COUNT=1"
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
          (is (= [["deleting" :orders {:id 1 :user_id :user-id}]]
                 @invocations)))

        (testing "deleting with a provided date will soft remove a single order by updating it if it exists"
          (reset! invocations [])
          (is (= (sut/delete! :user-id "2020-01-01" nil 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence "FREQ=DAILY;COUNT=1"
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
          (is (= [["updating" :recurrence_exceptions {:status "canceled"}
                   {:order_id 1, :order_date (t/to-db-date "2020-01-01")}]]
                 @invocations)))

        (testing "deleting with a provided date will soft remove a single order by adding an exception if none provided"
          (with-redefs [jdbc/execute-one! (fn [_ [q]]
                                            (when-not (str/includes? q "recurrence_exceptions")
                                              {:orders/order_date #inst "2020-01-01" :orders/recurrence "FREQ=DAILY;COUNT=1"}))]

            (reset! invocations [])
          (is (= (sut/delete! :user-id "2020-01-01" nil 1)
                 {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence "FREQ=DAILY;COUNT=1"
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
            (is (= [["inserting" :recurrence_exceptions {:order_id 1, :order_date (t/to-db-date "2020-01-01") :status "canceled"}]]
                   @invocations))))))))

(deftest test-change-state!
  (let [updates (atom [])]
    (with-redefs [jdbc.types/as-other identity
                  jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute-one! #(swap! updates conj %2)
                  sql/update! (fn [_ table _ val]
                                (swap! updates conj ["updating" table val]))
                  sql/query (constantly (raw-order-row :id 1 :status "waiting"))
                  sql/insert! (fn [_ table values] (swap! updates conj ["inserting" table values]))]
      (testing "states get changed - update when prexisiting exception"
        (reset! updates [])
        (is (= (sut/change-state! :user-id 1 "2020-01-01" "fulfilled")
               {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                               :who {:id 2, :name "mr blobby"}, :recurrence nil
                               :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
        (is (= [;; product updates
                ["UPDATE products SET amount = amount - ? WHERE name = ?" 12 "eggs"]
                ["UPDATE products SET amount = amount - ? WHERE name = ?" 3 "milk"]

                ;; check whether to insert or update
                ["SELECT * from recurrence_exceptions WHERE order_id = ? AND order_date = ?" 1 (t/to-db-date "2020-01-01")]
                ;; update
                ["updating" :recurrence_exceptions {:order_id 1, :order_date (t/to-db-date "2020-01-01")}]]
               @updates)))

      (testing "states get changed - insert when no such exception"
        (with-redefs [jdbc/execute-one! (fn [_ q]
                                          (swap! updates conj q)
                                          (when-not (str/includes? (first q) "recurrence_exceptions")
                                            (raw-order-row :id 1 :status "waiting")))]
          (reset! updates [])
          (is (= (sut/change-state! :user-id 1 "2020-01-01" "fulfilled")
                 {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                                 :who {:id 2, :name "mr blobby"}, :recurrence nil
                                 :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
          (is (= [;; product updates
                  ["UPDATE products SET amount = amount - ? WHERE name = ?" 12 "eggs"]
                  ["UPDATE products SET amount = amount - ? WHERE name = ?" 3 "milk"]

                  ;; check whether to insert or update
                  ["SELECT * from recurrence_exceptions WHERE order_id = ? AND order_date = ?" 1 (t/to-db-date "2020-01-01")]
                  ;; update
                  ["inserting" :recurrence_exceptions {:order_id 1, :order_date (t/to-db-date "2020-01-01"), :status "fulfilled"}]]
                 @updates))))))

  (testing "nothing happens if the state is already set"
    (let [updates (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! #(swap! updates conj %2)
                    sql/query (constantly (raw-order-row :id 1 :status "waiting"))]
        (is (= (sut/change-state! :user-id 1 "2020-01-01" "waiting")
               {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                               :who {:id 2, :name "mr blobby"}, :recurrence nil
                               :products {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 423}}}]}))
        (is (= @updates [])))))

  (testing "unknown states cause an exception"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  sql/query (constantly (raw-order-row :id 1 :status "waiting"))]
      (try
        (sut/change-state! :user-id 1 "bla bla bla")
        (is nil "The previous line should have failed")
        (catch Exception _ :ok)))))
