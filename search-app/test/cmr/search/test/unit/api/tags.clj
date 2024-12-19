(ns cmr.search.test.unit.api.tags
  "Tests to verify functionality in cmr.search.api.tags namespace."
  (:require
    [clojure.test :refer [deftest is]]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as util]
    [cmr.search.api.tags :as tags]))


(deftest tag-association-results->status-code-test
  (util/are3 [input return-code]
             (do
               (is (= return-code (tags/tag-association-results->status-code input))))

             "tags assoc has no errors, then returns 200 status code"
             '({:tag-association {:concept-id "TA1200000003-CMR", :revision-id 1}, :tagged-item {:concept-id "C1200000001-JM_PROV1"}})
             200

             "tags assoc has some errors and some successes, then returns 207 status code"
             '({:tag-association {:concept-id "TA1200000003-CMR", :revision-id 1}, :tagged-item {:concept-id "C1200000001-JM_PROV1"}} {:errors '("Collection [C1200000011-JM_PROV1] does not exist or is not visible."), :tagged-item {:concept-id "C1200000011-JM_PROV1"}})
             207

             "tags assoc has only errors, then returns 400 status code"
             '({:errors '("Collection [C1200000011-JM_PROV1] does not exist or is not visible."), :tagged-item {:concept-id "C1200000011-JM_PROV1"}})
             400))

(deftest tag-api-response-test
  (util/are3 [status-code input expected]
             (do
               (is (= expected (tags/tag-api-response status-code input))))

             "status-code 207 with errors and successes and warnings is given, returns response with separate status codes per association item"
             207
             '({:tag-association {:concept-id "TA1200000003-CMR", :revision-id 1}, :tagged-item {:concept-id "C1200000001-JM_PROV1"}}
               {:errors ["Collection [C1200000011-JM_PROV1] does not exist or is not visible."], :tagged-item {:concept-id "C1200000011-JM_PROV1"}})
             {:status 207
              :body "[{\"tag_association\":{\"concept_id\":\"TA1200000003-CMR\",\"revision_id\":1},\"tagged_item\":{\"concept_id\":\"C1200000001-JM_PROV1\"},\"status\":200},{\"errors\":[\"Collection [C1200000011-JM_PROV1] does not exist or is not visible.\"],\"tagged_item\":{\"concept_id\":\"C1200000011-JM_PROV1\"},\"status\":400}]"
              :headers {"Content-Type" mt/json}}

             "status-code 200 is given, returns data"
             200
             '({:tag-association {:concept-id "TA1200000003-CMR", :revision-id 1}, :tagged-item {:concept-id "C1200000001-JM_PROV1"}})
             {:status 200
              :body "[{\"tag_association\":{\"concept_id\":\"TA1200000003-CMR\",\"revision_id\":1},\"tagged_item\":{\"concept_id\":\"C1200000001-JM_PROV1\"}}]"
              :headers {"Content-Type" mt/json}}

             "status-code 400 is given, returns data"
             400
             '({:errors ["Collection [C1200000011-JM_PROV1] does not exist or is not visible."], :tagged-item {:concept-id "C1200000011-JM_PROV1"}})
             {:status 400
              :body "[{\"errors\":[\"Collection [C1200000011-JM_PROV1] does not exist or is not visible.\"],\"tagged_item\":{\"concept_id\":\"C1200000011-JM_PROV1\"}}]"
              :headers {"Content-Type" mt/json}}))
