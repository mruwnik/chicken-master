(ns chicken-master.products-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [chicken-master.products :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest test-get-all
  (testing "query is correct"
    (with-redefs [sql/query (fn [_ query]
                              (is (= query ["SELECT name, amount, price FROM products WHERE deleted IS NULL AND user_id = ?" "1"]))
                              [])]
      (sut/get-all "1")))

  (testing "correct format"
    (with-redefs [sql/query (constantly [{:products/name "eggs" :products/amount 12 :products/price nil}
                                         {:products/name "milk" :products/amount 3 :products/price 12}])]
      (is (= (sut/get-all "1") {:eggs {:amount 12 :price nil} :milk {:amount 3 :price 12}})))))

(deftest test-products-map
  (testing "no products"
    (is (nil? (sut/products-map :tx "1" {})))
    (is (nil? (sut/products-map :tx "1" nil))))

  (testing "correct sql"
    (with-redefs [sql/query (fn [_ query]
                              (is (= query ["SELECT id, name FROM products WHERE user_id = ? AND name IN (?, ?, ?)"
                                            "1" "eggs" "cows" "milk"]))
                              [])]
      (sut/products-map :tz "1" {:eggs 2 :cows 2 :milk 3})))

  (testing "correct format"
    (with-redefs [sql/query (constantly [{:products/id 1 :products/name "eggs"}
                                         {:products/id 2 :products/name "cows"}
                                         {:products/id 3 :products/name "milk"}])]
      (= (sut/products-map :tz "1" {:eggs 2 :cows 2 :milk 3})
         {"eggs" 1 "cows" 2 "milk" 3})))

  (testing "not all items need have ids"
    (with-redefs [sql/query (constantly [{:products/id 1 :products/name "eggs"}
                                         {:products/id 3 :products/name "milk"}])]
      (= (sut/products-map :tx "1" {:eggs 2 :cows 2 :milk 3})
         {"eggs" 1 "milk" 3}))))

(deftest test-update!
  (testing "each item gets updated"
    (let [inserts (atom [])
          update-query (str "INSERT INTO products (name, user_id, amount, price) VALUES(?, ?, ?, ?) "
                            "ON CONFLICT (name, user_id) "
                            "DO UPDATE SET deleted = NULL, amount = EXCLUDED.amount, price = EXCLUDED.price")]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute! #(swap! inserts conj %2)
                    sql/update! (constantly nil)
                    sql/query (constantly [])]
        (sut/update! :user-id {:eggs {:amount 2 :price 1} :milk {:amount 3 :price 2} :cows {:amount 2 :price 3}})
        (is (= (sort-by second @inserts)
               [[update-query "cows" :user-id 2 3]
                [update-query "eggs" :user-id 2 1]
                [update-query "milk" :user-id 3 2]])))))

  (testing "missing fields are ignored"
    (let [inserts (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute! #(swap! inserts conj %2)
                    sql/update! (constantly nil)
                    sql/query (constantly [])]
        (sut/update! :user-id {:eggs {:amount 2} :milk {:amount 3} :cows {}})
        (is (= (sort-by second @inserts)
               [[(str "INSERT INTO products (name, user_id, amount) VALUES(?, ?, ?) "
                      "ON CONFLICT (name, user_id) DO UPDATE "
                      "SET deleted = NULL, amount = EXCLUDED.amount") "eggs" :user-id 2]
                [(str "INSERT INTO products (name, user_id, amount) VALUES(?, ?, ?) "
                      "ON CONFLICT (name, user_id) DO UPDATE SET "
                      "deleted = NULL, amount = EXCLUDED.amount") "milk" :user-id 3]])))))

  (testing "non selected items get removed"
    (let [updates (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute! (constantly nil)
                    sql/update! (partial swap! updates conj)
                    sql/query (constantly [])]
        (sut/update! :user-id {:eggs {:amount 2} :milk {:amount 3} :cows {:amount 2}})
        (is (= @updates [{} :products {:deleted true} ["name NOT IN (?, ?, ?)" "eggs" "milk" "cows"]])))))

  (testing "non selected items get removed"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute! (constantly nil)
                  sql/update! (constantly nil)
                  sql/query (constantly [{:products/name "eggs" :products/amount 12 :products/price 1}
                                         {:products/name "milk" :products/amount 3 :products/price 2}])]
      (is (= (sut/update! :user-id {:eggs {:amount 2} :milk {:amount 3} :cows {:amount 2}})
             {:eggs {:amount 12 :price 1} :milk {:amount 3 :price 2}})))))

(deftest update-products-mapping-test
  (testing "items get removed"
    (let [item-id 123]
      (with-redefs [sut/products-map (constantly {"eggs" 1 "milk" 2 "carrots" 3})
                    sql/insert-multi! (constantly :ok)

                    sql/delete! (fn [_tx table id]
                                  (is (= table :bla_products))
                                  (is (= id {:bla_id item-id})))]
        (sut/update-products-mapping! :tx 123 :bla item-id {:eggs 34 :milk 25 :carrots 13}))))

  (testing "items get removed"
    (let [item-id 123]
      (with-redefs [sut/products-map (constantly {"eggs" 1 "milk" 2 "carrots" 3})
                    sql/delete! (constantly :ok)

                    sql/insert-multi! (fn [_tx table cols products]
                                        (is (= table :bla_products))
                                        (is (= cols [:bla_id :product_id :amount]))
                                        (is (= products [[item-id 1 34]
                                                         [item-id 2 25]
                                                         [item-id 3 13]])))]
        (sut/update-products-mapping! :tx 123 :bla item-id {:eggs 34 :milk 25 :carrots 13})))))
