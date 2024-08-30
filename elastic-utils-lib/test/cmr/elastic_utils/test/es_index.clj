(ns cmr.elastic-utils.test.es-index
  "Tests for the cmr.elastic-utils.search.es-index namespace"
  (:require 
   [clojure.test :refer [deftest is testing]]
   [cmr.elastic-utils.search.es-index :as es-index]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]))

(deftest test-query->execution-params
  (let [query->execution-params #'es-index/query->execution-params
        condition (gc/or-conds (map #(qm/string-conditions :consortiums [%])
                                    ["CWIC" "FEDEO" "GEOSS" "CEOS" "EOSDIS"]))]
    (testing "query include remove-source"
      (let [query (qm/query {:concept-type :collection
                             :result-format :xml
                             :condition condition
                             :page-size :unlimited
                             :remove-source true})
            execution-params (query->execution-params query)]
        (is (= false
               (:_source execution-params)))))
    (testing "query doesn't include remove-source"
      (let [query (qm/query {:concept-type :collection
                             :result-format :xml
                             :condition condition
                             :page-size :unlimited})
            execution-params (query->execution-params query)]
        (is (not (= false
                    (:_source execution-params))))))))
