(ns cmr.metadata-db.int-test.concepts.get-concept-id-test
  "Contains integration tests for getting concepts. Tests gets with various
  configurations including checking for proper error handling."
  (:require
   [cheshire.core :as cheshire]
   [clj-http.client :as client]
   [clojure.test :refer :all]
   [cmr.common.util :as cutil]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]
   [cmr.metadata-db.services.messages :as messages]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;;; tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest get-concept-id-test
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll (concepts/create-and-save-concept :collection provider-id 1)]
      (testing "collection"
        (is (= {:status 200
                :concept-id (:concept-id coll)
                :errors nil}
               (util/get-concept-id :collection provider-id (:native-id coll)))))
      (testing "multiple retrievals"
        (is (= (util/get-concept-id :collection provider-id (:native-id coll))
               (util/get-concept-id :collection provider-id (:native-id coll)))))
      (testing "granule"
        (let [gran (concepts/create-and-save-concept :granule provider-id coll 1)]
          (is (= {:status 200
                  :concept-id (:concept-id gran)
                  :errors nil}
                 (util/get-concept-id :granule provider-id (:native-id gran)))))))))

(deftest fail-to-get-concept-id-for-non-existing-concept
  (doseq [provider-id ["REG_PROV" "SMAL_PROV"]]
    (let [coll (concepts/create-and-save-concept :collection provider-id 1)
          gran (concepts/create-and-save-concept :granule provider-id coll 1)]
      (testing "native-id does not exist"
        (are [concept-type native-id]
             (= {:status 404
                 :concept-id nil
                 :errors [(messages/missing-concept-id concept-type provider-id native-id)]}
                (util/get-concept-id concept-type provider-id native-id))
             :collection "foo"
             :granule "foo"))
      (testing "concept type does not exist"
        (are [concept-type native-id]
             (= {:status 400
                 :concept-id nil
                 :errors ["[foo] is not a valid concept type."]}
                (util/get-concept-id concept-type provider-id native-id))
             :foo (:native-id coll)
             :foo (:native-id gran)))
      (testing "providers do not exist"
        (are [concept-type pid native-id]
             (= {:status 404
                 :concept-id nil
                 :errors ["Provider with provider-id [PROV3] does not exist."]}
                (util/get-concept-id concept-type pid native-id))
             :collection "PROV3" (:native-id coll)
             :granule "PROV3" (:native-id gran))))))
