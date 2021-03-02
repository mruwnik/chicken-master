(ns clj.chicken-master.customers-test
  (:require
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as sql]
   [chicken-master.customers :as sut]
   [chicken-master.orders :as orders]
   [clojure.test :refer [deftest is testing]]))

(deftest test-get-all
  (testing "query is correct"
    (with-redefs [sql/query (fn [_ query]
                              (is (= query ["select * from customers where deleted is null AND user_id = ?" "1"]))
                              [])]
      (sut/get-all "1")))

  (testing "results are mapped correctly"
    (with-redefs [sql/query (constantly [{:customers/id 1 :customers/name "mr blobby" :bla 123}])]
      (= (sut/get-all "1")
         [{:id 1 :name "mr blobby"}]))))

(deftest test-create!
  (testing "correct format is returned"
    (with-redefs [jdbc/execute! (constantly [])
                  sql/query (constantly [{:customers/id 1 :customers/name "mr blobby" :bla 123}])]
      (= (sut/create! "1" "mr blobby")
         {:customers [{:id 1 :name "mr blobby"}]}))))


(deftest test-delete!
  (testing "correct format returned"
    (with-redefs [orders/get-all (constantly :orders)
                  sql/update! (constantly [])
                  sql/query (constantly [{:customers/id 1 :customers/name "mr blobby" :bla 123}])]
      (= (sut/delete! "1" "2")
         {:customers [{:id 1 :name "mr blobby"}]
          :orders :orders}))))
