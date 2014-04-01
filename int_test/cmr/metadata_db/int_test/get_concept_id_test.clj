(ns cmr.metadata-db.int-test.get-concept-id-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.common.util :as cutil]
            [cmr.metadata-db.data.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-get-concept-id-test
  "Get a concept-id for a given concept-type, provider-id, and native-id."
  (let [concept (util/concept)
        _ (util/save-concept concept)
        {:keys [status concept-id]} (util/get-concept-id (name (:concept-type concept))
                                                         (:provider-id concept)
                                                         (:native-id concept))]
    (is (= status 200))
    (is (= concept-id "C1000000000-PROV1"))))

(deftest mdb-get-concept-id-repeatedly-test
  "Get a concept-id repeatedly to verify that it is the same each time."
  (let [concept (util/concept)
        _ (util/save-concept concept)
        concept-id1-map (util/get-concept-id (name (:concept-type concept))
                                             (:provider-id concept)
                                             (:native-id concept))
        concept-id2-map (util/get-concept-id (name (:concept-type concept))
                                             (:provider-id concept)
                                             (:native-id concept))]
    (is (= concept-id1-map concept-id2-map))))

(deftest mdb-fail-to-get-concept-id-for-non-existing-concept
  "Requests for concept-ids for concepts that have not been saved should return a 404."
  (let [concept (util/concept)
        response (util/get-concept-id (name (:concept-type concept))
                                      (:provider-id concept)
                                      (:native-id concept))
        {:keys [status concept-id error-messages]} response]
    (is (= status 404))
    (is (= error-messages
           [(messages/missing-concept-id-msg
              (name (:concept-type concept))
              (:provider-id concept)
              (:native-id concept))]))))

