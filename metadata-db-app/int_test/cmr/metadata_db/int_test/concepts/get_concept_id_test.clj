(ns cmr.metadata-db.int-test.concepts.get-concept-id-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.metadata-db.int-test.utility :as util]
            [cmr.common.util :as cutil]
            [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture "PROV1"))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-concept-id-test
  (let [coll (util/create-and-save-collection "PROV1" 1)]
    (testing "collection"
      (is (= {:status 200
              :concept-id (:concept-id coll)
              :errors nil}
             (util/get-concept-id "collection" "PROV1" (:native-id coll)))))
    (testing "multiple retrievals"
      (is (= (util/get-concept-id "collection" "PROV1" (:native-id coll))
             (util/get-concept-id "collection" "PROV1" (:native-id coll)))))
    (testing "granule"
      (let [gran (util/create-and-save-granule "PROV1" (:concept-id coll) 1)]
        (is (= {:status 200
                :concept-id (:concept-id gran)
                :errors nil}
               (util/get-concept-id "granule" "PROV1" (:native-id gran))))))))

(deftest fail-to-get-concept-id-for-non-existing-concept
  (let [coll (util/create-and-save-collection "PROV1" 1)
        gran (util/create-and-save-granule "PROV1" (:concept-id coll) 1)]
    (testing "native-id does not exist"
      (are [concept-type provider-id native-id]
           (= {:status 404
               :concept-id nil
               :errors [(messages/missing-concept-id concept-type provider-id native-id)]}
              (util/get-concept-id concept-type provider-id native-id))
           "collection" "PROV1" "foo"
           "granule" "PROV1" "foo"))
    (testing "concept type does not exist"
      (are [concept-type provider-id native-id]
           (= {:status 400
               :concept-id nil
               :errors ["[foo] is not a valid concept type."]}
              (util/get-concept-id concept-type provider-id native-id))
           "foo" "PROV1" (:native-id coll)
           "foo" "PROV1" (:native-id gran)))
    (testing "providers do not exist"
      (are [concept-type provider-id native-id]
           (= {:status 404
               :concept-id nil
               :errors ["Providers with provider-ids [PROV5] do not exist."]}
              (util/get-concept-id concept-type provider-id native-id))
           "collection" "PROV5" (:native-id coll)
           "granule" "PROV5" (:native-id gran)))))

