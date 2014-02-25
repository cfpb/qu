(ns qu.test.cache
  (:refer-clojure :exclude [sort])
  (:require [clojure.test :refer :all]
            [qu.query :as q]
            [qu.cache :refer :all]))

(deftest test-query-to-key
  (let [query1 (q/map->Query {:select "state_id, county_id, MAX(tax_returns)" :group "state_id, county_id" :metadata {:database "test"} :slice "test"})
        query2 (q/map->Query {:select "state_id,county_id,MAX(tax_returns)" :group "state_id,county_id" :metadata {:database "test"} :slice "test"})]
        
    (testing "it eliminates space in the SELECT and GROUP BY fields"
      (is (= (query-to-key query1) (query-to-key query2))))

    (testing "different WHERE queries make different keys"
      (is (not (= (query-to-key (assoc query1 :where "state_id = 1"))
                  (query-to-key (assoc query1 :where "state_id = 2"))))))

    (testing "different ORDER BY queries make the same key"
      (is (= (query-to-key (assoc query1 :orderBy "state_id"))
             (query-to-key (assoc query1 :orderBy "max_tax_returns")))))

    (testing "different LIMIT and OFFSET queries make the same key"
      (is (= (query-to-key (assoc query1 :limit 10))
             (query-to-key (assoc query1 :limit 20 :offset 10)))))))

;; (run-tests)
