(ns chicken-master.customers-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [chicken-master.customers :as sut]
   [chicken-master.products :as products]
   [clojure.test :refer [deftest is testing]]))

(def sample-customers
  [{:customers/name "klient 1", :customers/id 1
    :customer_groups/name "group1", :customer_groups/id 1,
    :customer_group_products/amount 2, :products/name "eggs"}
   {:customers/name "klient 1", :customers/id 1
    :customer_groups/name "group1", :customer_groups/id 1,
    :customer_group_products/amount 32, :products/name "milk"}

   {:customers/name "klient 1", :customers/id 1
    :customer_groups/name "group 2", :customer_groups/id 2,
    :customer_group_products/amount 1, :products/name "milk"}
   {:customers/name "klient 1", :customers/id 1
    :customer_groups/name "group 2", :customer_groups/id 2,
    :customer_group_products/amount 6, :products/name "eggs"}
   {:customers/name "klient 1", :customers/id 1
    :customer_groups/name "group 2", :customer_groups/id 2,
    :customer_group_products/amount 89, :products/name "carrots"}

   {:customers/name "klient 2", :customers/id 2
    :customer_groups/name "group 3", :customer_groups/id 3,
    :customer_group_products/amount 41, :products/name "milk"}
   {:customers/name "klient 2", :customers/id 2
    :customer_groups/name "group 3", :customer_groups/id 3,
    :customer_group_products/amount 6, :products/name "eggs"}])

(deftest format-products-tests
  (testing "products amounts get formatted properly"
    (is (= (sut/insert-products {} {:id 1 :name "bla"} {})
           {"bla" {:id 1 :products {}}}))

    (is (= (sut/insert-products {"ble" {:id 32}} {:id 1 :name "bla"} {})
           {"ble" {:id 32} "bla" {:id 1 :products {}}}))

    (is (= (sut/insert-products {} {:id 1 :name "bla"}
                                [{:products/name "milk" :customer_group_products/amount 1}
                                 {:products/name "carrots" :customer_group_products/amount 12}
                                 {:products/name "eggs" :customer_group_products/amount 3}])
           {"bla" {:id 1 :products {:eggs 3 :carrots 12 :milk 1}}})))

  (testing "extracting product groups works"
    (is (= (sut/extract-product-groups {:ble "ble"} sample-customers)
           {:ble "ble"
            :product-groups {"group1" {:id 1, :products {:eggs 2, :milk 32}},
                             "group 2" {:id 2, :products {:milk 1, :eggs 6, :carrots 89}},
                             "group 3" {:id 3, :products {:milk 41, :eggs 6}}}})))

  (testing "extracting product groups stops if no values"
    (is (= (sut/extract-product-groups {:ble "ble"} nil)
           {:ble "ble"}))

    (is (= (sut/extract-product-groups {:ble "ble"} [])
           {:ble "ble" :product-groups {}}))))

(deftest test-get-all
  (testing "query is correct"
    (with-redefs [sql/query (fn [_ query]
                              (is (= query [sut/users-select-query "1"]))
                              [])]
      (sut/get-all "1")))

  (testing "results are mapped correctly"
    (with-redefs [sql/query (constantly [{:customers/id 1 :customers/name "mr blobby" :bla 123}])]
      (is (= (sut/get-all 2)
             [{:id 1 :name "mr blobby" :product-groups {}}]))))

  (testing "customer groups are mapped correctly"
    (with-redefs [sql/query (constantly sample-customers)]
      (is (= (sut/get-all "1")
             [{:id 1 :name "klient 1" :product-groups {"group1" {:id 1 :products {:eggs 2 :milk 32}}
                                                       "group 2" {:id 2 :products {:milk 1 :eggs 6 :carrots 89}}}}
              {:id 2 :name "klient 2" :product-groups {"group 3" {:id 3 :products {:milk 41 :eggs 6}}}}])))))

(deftest test-create!
  (testing "correct format is returned"
    (with-redefs [jdbc/execute! (constantly [])
                  sql/query (constantly [{:customers/id 1 :customers/name "mr blobby" :bla 123}])]
      (is (= (sut/create! "1" "mr blobby")
             {:customers [{:id 1 :name "mr blobby" :product-groups {}}]})))))


(deftest save-product-group-test
  (let [user-id 1
        customer-id 2
        group-id 123]
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  sql/insert! (constantly {:customer_groups/id group-id})
                  sql/delete! (fn [_tx table id]
                                (is (= table :customer_group_products))
                                (is (= id {:customer_group_id group-id})))

                  products/products-map (constantly {"eggs" 1 "milk" 2 "carrots" 3})

                  sql/insert-multi! (fn [_tx table cols products]
                                      (is (= table :customer_group_products))
                                      (is (= cols [:customer_group_id :product_id :amount]))
                                      (is (= products [[group-id 1 34]
                                                       [group-id 2 25]
                                                       [group-id 3 13]]))
                                      :ok)]
      (testing "the correct query is used to check if group exists"
        (with-redefs [jdbc/execute-one!
                      (fn [_ query]
                        (is (= query ["SELECT * FROM customer_groups WHERE user_id = ? AND customer_id = ? AND id =?"
                                      user-id customer-id nil]))
                        nil)]
          (sut/save-product-group user-id customer-id {:name "bla" :products {:eggs 34 :milk 25 :carrots 13}})))

      (testing "product groups get created"
        (with-redefs [jdbc/execute-one! (constantly nil) ; the group doesn't yet exist
                      sql/insert! (fn [_ _ group]
                                    (is (= group {:name "bla" :customer_id customer-id :user_id user-id}))
                                    {:customer_groups/id group-id}) ; create a new group
                      sql/update! (fn [&args] (is nil "The group shouldn't be updated"))]
        (sut/save-product-group user-id customer-id {:name "bla" :products {:eggs 34 :milk 25 :carrots 13}})))

      (testing "existing product groups get updated"
        (with-redefs [jdbc/execute-one! (constantly true) ; the group should exist
                      sql/update! (fn [_tx table item query]
                                    (is (= table :customer_groups))
                                    (is (= item {:name "bla"}))
                                    (is (= query {:id group-id})))
                      sql/insert! (fn [&args] (is nil "The group shouldn't be created"))]
          (sut/save-product-group user-id customer-id
                                  {:id group-id :name "bla" :products {:eggs 34 :milk 25 :carrots 13}}))))))
