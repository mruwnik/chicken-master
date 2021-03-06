(ns chicken-master.products-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [chicken-master.products :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest test-get-all
  (testing "query is correct"
    (with-redefs [sql/query (fn [_ query]
                              (is (= query ["SELECT * FROM products WHERE deleted IS NULL AND user_id = ?" "1"]))
                              [])]
      (sut/get-all "1")))

  (testing "correct format"
    (with-redefs [sql/query (constantly [{:products/name "eggs" :products/amount 12}
                                         {:products/name "milk" :products/amount 3}])]
      (is (= (sut/get-all "1") {:eggs 12 :milk 3})))))

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
          update-query "INSERT INTO products (name, amount, user_id) VALUES(?, ?, ?)\n                    ON CONFLICT (name, user_id) DO UPDATE SET amount = EXCLUDED.amount, deleted = NULL"]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute! #(swap! inserts conj %2)
                    sql/update! (constantly nil)
                    sql/query (constantly [])]
        (sut/update! :user-id {:eggs 2 :milk 3 :cows 2})
        (is (= (sort @inserts)
               [[update-query "cows" 2 :user-id]
                [update-query "eggs" 2 :user-id]
                [update-query "milk" 3 :user-id]])))))

  (testing "non selected items get removed"
    (let [updates (atom [])]
      (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                    jdbc/execute! (constantly nil)
                    sql/update! (partial swap! updates conj)
                    sql/query (constantly [])]
        (sut/update! :user-id {:eggs 2 :milk 3 :cows 2})
        (is (= @updates [{} :products {:deleted true} ["name NOT IN (?, ?, ?)" "eggs" "milk" "cows"]])))))

  (testing "non selected items get removed"
    (with-redefs [jdbc/transact (fn [_ f & args] (apply f args))
                  jdbc/execute! (constantly nil)
                  sql/update! (constantly nil)
                  sql/query (constantly [{:products/name "eggs" :products/amount 12}
                                         {:products/name "milk" :products/amount 3}])]
        (is (= (sut/update! :user-id {:eggs 2 :milk 3 :cows 2}) {:eggs 12 :milk 3})))))
