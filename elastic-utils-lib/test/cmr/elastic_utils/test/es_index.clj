(ns cmr.elastic-utils.test.es-index
  "Tests for the cmr.elastic-utils.search.es-index namespace"
  (:require
   [clojure.test :refer [deftest is testing]]
   [cmr.common.services.search.query-model :as qm]
   [cmr.elastic-utils.es-helper :as es-helper]
   [cmr.elastic-utils.search.es-group-query-conditions :as gc]
   [cmr.elastic-utils.search.es-index :as es-index]))

(def clause-limit-message
  "The search is creating more clauses than allowed by CMR. Please narrow your search.")

(defn- send-query-with-es-error
  [status body]
  (with-redefs [es-index/context->conn (constantly nil)
                es-helper/search (fn [& _]
                                   (throw (ex-info "Elasticsearch request failed"
                                                   {:status status
                                                    :body body})))]
    (#'es-index/do-send-with-retry
     {}
     {:index-name "test-index" :type-name "collection"}
     {}
     1)))

(deftest clause-limit-errors-are-payload-too-large
  (doseq [[description body]
          [["Elasticsearch 7 default clause limit error"
            "maxClauseCount is set to 1024"]
           ["Elasticsearch 7 configured clause limit error"
            "maxClauseCount is set to 4096"]
           ["Elasticsearch 8 clause limit error"
            (str "{\"error\":{\"root_cause\":[{\"type\":\"illegal_argument_exception\","
                 "\"reason\":\"Query rewrite failed: too many clauses\"}]},\"status\":400}")]]]
    (testing description
      (let [exception (try
                        (send-query-with-es-error 400 body)
                        nil
                        (catch clojure.lang.ExceptionInfo e
                          e))]
        (is (= :payload-too-large (:type (ex-data exception))))
        (is (= [clause-limit-message] (:errors (ex-data exception))))))))

(deftest clause-limit-errors-require-bad-request-status
  (let [exception (try
                    (send-query-with-es-error 500 "Query rewrite failed: too many clauses")
                    nil
                    (catch clojure.lang.ExceptionInfo e
                      e))]
    (is (nil? (:type (ex-data exception))))
    (is (= 500 (:status (ex-data (ex-cause exception)))))))

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
