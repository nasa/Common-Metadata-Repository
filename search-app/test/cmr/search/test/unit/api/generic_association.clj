(ns cmr.search.test.unit.api.generic-association
  "Tests to verify functionality in cmr.search.api.generic-association namespace."
  (:require
    [clojure.test :refer [deftest is]]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as util]
    [cmr.search.api.generic-association :as gen-assoc]))

(deftest generic-assoc-results->status-code-test
  (util/are3 [input status-code]
             (do
               (is (= status-code (gen-assoc/generic-assoc-results->status-code input))))

             "generic-assoc: some errors and some successes, return 207"
             '({:generic-association {:concept-id "GA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:generic-association {:concept-id "GA1200000028-CMR" :revision-id 1} :associated-item {:concept-id "C1200000014-PROV1"}}
               {:errors ["Concept [C1200465592-JM_PROV1] does not exist or is not visible."] :associated-item {:concept-id "C1200000013-PROV1"}})
             207

             "generic-assoc: no errors, return 400"
             '({:errors ["Concept [C1200465592-JM_PROV1] does not exist or is not visible."] :associated-item {:concept-id "C1200000013-PROV1"}})
             400

             "generic-assoc: all successes, return 200"
             '({:generic-association {:concept-id "GA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:generic-association {:concept-id "GA1200000028-CMR" :revision-id 1} :associated-item {:concept-id "C1200000014-PROV1"}})
             200))

(deftest api-response-test
  (util/are3 [status-code input expected]
             (do
               (is (= expected (gen-assoc/api-response status-code input))))

             "status-code 207 with errors and successes and warnings is given, returns response with separate status codes per association item"
             207
             '({:generic-association {:concept-id "GA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:generic-association {:concept-id "GA1200000028-CMR" :revision-id 1} :associated-item {:concept-id "C1200000014-PROV1"}}
               {:errors ["Concept [C1200465592-JM_PROV1] does not exist or is not visible."] :associated-item {:concept-id "C1200000013-PROV1"}})
             {:status 207
              :body "[{\"generic_association\":{\"concept_id\":\"GA1200000026-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000013-PROV1\"},\"status\":200},{\"generic_association\":{\"concept_id\":\"GA1200000028-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000014-PROV1\"},\"status\":200},{\"errors\":[\"Concept [C1200465592-JM_PROV1] does not exist or is not visible.\"],\"associated_item\":{\"concept_id\":\"C1200000013-PROV1\"},\"status\":400}]"
              :headers {"Content-Type" mt/json}}

             "status-code 200 is given, returns data"
             200
             '({:generic-association {:concept-id "GA1200000026-CMR" :revision-id 1} :associated-item {:concept-id "C1200000013-PROV1"}}
               {:generic-association {:concept-id "GA1200000028-CMR" :revision-id 1} :associated-item {:concept-id "C1200000014-PROV1"}})
             {:status 200
              :body "[{\"generic_association\":{\"concept_id\":\"GA1200000026-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000013-PROV1\"}},{\"generic_association\":{\"concept_id\":\"GA1200000028-CMR\",\"revision_id\":1},\"associated_item\":{\"concept_id\":\"C1200000014-PROV1\"}}]"
              :headers {"Content-Type" mt/json}}

             "status-code 400 is given, returns data"
             400
             '({:errors ["Concept [C1200465592-JM_PROV1] does not exist or is not visible."] :associated-item {:concept-id "C1200000013-PROV1"}})
             {:status 400
              :body "[{\"errors\":[\"Concept [C1200465592-JM_PROV1] does not exist or is not visible.\"],\"associated_item\":{\"concept_id\":\"C1200000013-PROV1\"}}]"
              :headers {"Content-Type" mt/json}}))
