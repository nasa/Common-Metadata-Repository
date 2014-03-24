(ns cmr.metadata-db.int-test.get-concept-id-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.common.util :as cutil]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each util/reset-database-fixture)

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest mdb-get-concept-id-test
  "Get a concept-id for a given concept-type, provider-id, and native-id."
  (let [concept (util/concept)
        {:keys [status concept-id]} (util/get-concept-id (name (:concept-type concept))
                                                         (:provider-id concept)
                                                         (:native-id concept))]
    (is (and (= status 200) (= concept-id (:concept-id concept))))))

(deftest mdb-get-concept-id-repeatedly-test
  "Get a concept-id repeatedly to verify that it is the same each time."
  (let [concept (util/concept)
        concept-id1-map (util/get-concept-id (name (:concept-type concept))
                                             (:provider-id concept)
                                             (:native-id concept))
        concept-id2-map (util/get-concept-id (name (:concept-type concept))
                                             (:provider-id concept)
                                             (:native-id concept))]
    (is (= concept-id1-map concept-id2-map))))

(deftest mdb-get-concept-id-verify-different-sequence-numbers
  "Concepts with the same provider id and concept type should get concept-ids that differ only in the sequence number."
  (let [concept (util/concept)
        concept-id1 (:concept-id (util/get-concept-id (name (:concept-type concept))
                                                      (:provider-id concept)
                                                      (:native-id concept)))
        concept-id2 (:concept-id (util/get-concept-id (name (:concept-type concept))
                                                      (:provider-id concept)
                                                      (str (:native-id concept) "EXTRA TEXT")))
        concept1-extracted-fields (cutil/parse-concept-id concept-id1)
        concept2-extracted-fields (cutil/parse-concept-id concept-id2)]
    (is (= (:concept-prefix concept1-extracted-fields) (:concept-prefix concept2-extracted-fields)))
    (is (not= (:sequence-number concept1-extracted-fields) (:sequence-number concept2-extracted-fields)))
    (is (= (:provider-id concept1-extracted-fields) (:provider-id concept2-extracted-fields)))))