(ns cmr.metadata-db.int-test.concepts.transaction-id-test
  "Contains a test to verify that when saving concepts in no particular order (provider, concept
  type) they get incrementing transaction-ids."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

;;; fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Set up REG_PROV as regular provider and SMAL_PROV as a small provider
(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV" :small true}))

;; Verify that transaction-ids increment when new concepts are saved regardless of which provider
;; or concept type.
(deftest incrementing-transcation-ids
  (testing "Concepts saved in mixed order get incrementing transaction-ids"
    (let [coll-reg (util/create-and-save-collection "REG_PROV" 1)
          gran-reg (util/create-and-save-granule "REG_PROV" coll-reg 1)
          serv1 (concepts/create-and-save-concept :service "REG_PROV" 1)
          tag1 (util/create-and-save-tag 1)
          coll-small (util/create-and-save-collection "SMAL_PROV" 2)
          group-small (util/create-and-save-group "SMAL_PROV" 1)
          tag2 (util/create-and-save-tag 2)
          serv2 (concepts/create-and-save-concept :service "REG_PROV" 2)
          gran-small (util/create-and-save-granule "SMAL_PROV" coll-small 2)
          group-reg (util/create-and-save-group "REG_PROV" 1)
          concept-ids (map :concept-id [coll-reg gran-reg serv1 tag1 coll-small group-small
                                        tag2 serv2 gran-small group-reg])
          trans-ids (distinct
                     (map :transaction-id
                          (:concepts (util/get-latest-concepts concept-ids))))]
      (is (= 10 (count trans-ids)))
      (is (apply < trans-ids)))))
