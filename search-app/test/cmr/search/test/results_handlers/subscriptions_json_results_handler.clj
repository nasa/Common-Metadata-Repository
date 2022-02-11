(ns cmr.search.test.results-handlers.subscriptions-json-results-handler
  "Handles extracting elasticsearch subscription results and converting them into a JSON search response."
  (:require
   [clojure.test :refer :all]
   [cmr.search.results-handlers.subscriptions-json-results-handler]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]))

(deftest results-formatting-test
  (let [result {:_source {:subscription-name "subscription-name"
                          :subscriber-id "subscriber-id"
                          :revision-id 1
                          :collection-concept-id "collection-concept-id"
                          :deleted false
                          :provider-id "provider-id"
                          :native-id "native-id"
                          :concept-id "concept-id"}}
        query {:concept-type :subscription
               :format :json}]
    (is (= #{:concept_id 
             :revision_id
             :provider_id
             :native_id
             :name
             :subscriber_id
             :collection_concept_id} 
           (set (keys (elastic-results/elastic-result->query-result-item nil query result)))))))
