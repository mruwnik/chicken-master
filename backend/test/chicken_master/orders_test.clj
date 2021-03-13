(ns chicken-master.orders-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [chicken-master.orders :as sut]
   [chicken-master.products :as products]
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]))

(defn raw-order-row [& {:keys [id notes status date user_id user_name products]
                        :or {id 1 notes "note" status "pending" date #inst "2020-01-01"
                             user_id 2 user_name "mr blobby" products {:eggs 12 :milk 3}}}]
  (if products
    (for [[product amount] products]
      (merge #:orders{:id id :notes notes :status status :order_date date}
             #:customers{:id user_id :name user_name}
             {:products/name (name product) :order_products/amount amount}))
    [(merge #:orders{:id id :notes notes :status status :order_date date}
            #:customers{:id user_id :name user_name}
            {:products/name nil :order_products/amount nil})]))

(deftest structure-order-test
  (testing "basic structure"
    (is (= (sut/structure-order (raw-order-row))
           {:id 1, :notes "note", :state :pending, :day "2020-01-01",
            :who {:id 2, :name "mr blobby"},
            :products {:eggs 12 :milk 3}})))

  (testing "missing products"
    (is (= (sut/structure-order (raw-order-row :products nil))
           {:id 1, :notes "note", :state :pending, :day "2020-01-01",
            :who {:id 2, :name "mr blobby"},
            :products {}}))))

(deftest test-get-order
  (testing "correct values returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.id = ? AND o.user_id = ?"))
                              (is (= params [123 "1"]))
                              (raw-order-row))]
      (is (= (sut/get-order :tx "1" 123)
             {:id 1, :notes "note", :state :pending, :day "2020-01-01",
              :who {:id 2, :name "mr blobby"},
              :products {:eggs 12 :milk 3}}))))

  (testing "Only 1 item returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.id = ? AND o.user_id = ?"))
                              (is (= params [123 "1"]))
                              (concat (raw-order-row)
                                      (raw-order-row :id 21)))]
      (is (= (sut/get-order :tx "1" 123)
             {:id 1, :notes "note", :state :pending, :day "2020-01-01",
              :who {:id 2, :name "mr blobby"},
              :products {:eggs 12 :milk 3}})))))

(deftest test-get-all
  (testing "correct values returned"
    (with-redefs [sql/query (fn [_ [query & params]]
                              (is (str/ends-with? query "WHERE o.user_id = ?"))
                              (is (= params ["1"]))
                              (concat
                               (raw-order-row :id 1 :status "waiting")
                               (raw-order-row :id 2 :date #inst "2020-01-03")
                               (raw-order-row :id 3 :user_id 43 :user_name "John")
                               (raw-order-row :id 4)))]
      (is (= (sut/get-all "1")
             {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                             :who {:id 2, :name "mr blobby"},
                             :products {:eggs 12 :milk 3}}
                            {:id 3, :notes "note", :state :pending, :day "2020-01-01",
                             :who {:id 43, :name "John"},
                             :products {:eggs 12 :milk 3}}
                            {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                             :who {:id 2, :name "mr blobby"},
                             :products {:eggs 12 :milk 3}}]
              "2020-01-03" [{:id 2, :notes "note", :state :pending, :day "2020-01-03",
                             :who {:id 2, :name "mr blobby"},
                             :products {:eggs 12 :milk 3}}]})))))

(deftest test-replace!
  (testing "basic replace order"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs 12 :milk 3}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly nil)
                    products/products-map (constantly {"eggs" 1 "milk" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount]))
                                        (is (= values [[1 1 12] [1 2 3]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting")
                                           (raw-order-row :id 4)))]
    (is (= (sut/replace! :user-id order)
           {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}
                          {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}]})))))

  (testing "replace order from different day"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-02",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs 12 :milk 3}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01"})
                    products/products-map (constantly {"eggs" 1 "milk" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount]))
                                        (is (= values [[1 1 12] [1 2 3]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting" :date #inst "2020-01-02")
                                           (raw-order-row :id 4)))]
    (is (= (sut/replace! :user-id order)
           {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}]
            "2020-01-02" [{:id 1, :notes "note", :state :waiting, :day "2020-01-02",
                          :who {:id 2, :name "mr blobby"},
                          :products {:eggs 12 :milk 3}}]})))))

  (testing "unknown products are ignored"
    (let [order {:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                 :who {:id 2, :name "mr blobby"},
                 :products {:eggs 12 :milk 3}}]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! (constantly nil)
                    products/products-map (constantly {"eggs" 1 "candles" 2})
                    sut/upsert-order! (fn [_ _ _ o] (is (= o order)) 1)
                    sql/delete! (fn [_ table by]
                                 (is (= table :order_products))
                                 (is (= by {:order_id (:id order)})))
                    sql/insert-multi! (fn [_ _ cols values]
                                        (is (= cols [:order_id :product_id :amount]))
                                        (is (= values [[1 1 12]])))
                    sql/query (constantly (concat
                                           (raw-order-row :id 1 :status "waiting")
                                           (raw-order-row :id 4)))]
    (is (= (sut/replace! :user-id order)
           {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}
                          {:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}]}))))))

(deftest test-delete!
  (testing "non deleted items from day are returned"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute-one! (constantly {:orders/order_date #inst "2020-01-01"})
                  sql/delete! (fn [_ table by]
                                (is (= table :orders))
                                (is (= by {:id 1 :user_id :user-id})))
                  sql/query (constantly (raw-order-row :id 4))]
    (is (= (sut/delete! :user-id 1)
           {"2020-01-01" [{:id 4, :notes "note", :state :pending, :day "2020-01-01",
                           :who {:id 2, :name "mr blobby"},
                           :products {:eggs 12 :milk 3}}]}))))

  (testing "nothing returned if no date set for the given order"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute-one! (constantly nil)
                  sql/delete! (fn [_ table by]
                                (is (= table :orders))
                                (is (= by {:id 1 :user_id :user-id})))
                  sql/query (constantly (raw-order-row :id 4))]
      (is (nil? (sut/delete! :user-id 1))))))

(deftest test-change-state!
  (testing "states get changed"
    (let [updates (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! #(swap! updates conj %2)
                    sql/update! (fn [_ table _ val]
                                  (is (= table :orders))
                                  (is (= val {:id 1})))
                    sql/query (constantly (raw-order-row :id 1 :status "waiting"))]
        (is (= (sut/change-state! :user-id 1 "fulfilled")
               {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                               :who {:id 2, :name "mr blobby"},
                               :products {:eggs 12 :milk 3}}]}))
        (is (= @updates [["UPDATE products SET amount = amount - ? WHERE name = ?" 12 "eggs"]
                         ["UPDATE products SET amount = amount - ? WHERE name = ?" 3 "milk"]])))))

  (testing "nothing happens if the state is already set"
    (let [updates (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute-one! #(swap! updates conj %2)
                    sql/query (constantly (raw-order-row :id 1 :status "waiting"))]
        (is (= (sut/change-state! :user-id 1 "waiting")
               {"2020-01-01" [{:id 1, :notes "note", :state :waiting, :day "2020-01-01",
                               :who {:id 2, :name "mr blobby"},
                               :products {:eggs 12 :milk 3}}]}))
        (is (= @updates [])))))

  (testing "unknown states cause an exception"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  sql/query (constantly (raw-order-row :id 1 :status "waiting"))]
      (try
        (sut/change-state! :user-id 1 "bla bla bla")
        (is nil "The previous line should have failed")
        (catch Exception _ :ok)))))
