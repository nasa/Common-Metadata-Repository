(ns cmr.metadata-db.int-test.delete-provider-cascading-test
  "Tests that delete a provider cascading delete its concepts."
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (join-fixtures
                      [(util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                    {:provider-id "PROV1" :small false})]))

(defn- concept-deleted?
  "Returns true if the concept does not exist in metadata db"
  [concept]
  (= 404 (:status (util/get-concept-by-id (:concept-id concept)))))

(defn- concept-exist?
  "Returns true if the concept exists in metadata db as a record including tombstone"
  [concept]
  (= 200 (:status (util/get-concept-by-id (:concept-id concept)))))

(deftest delete-provider-cascade-delete-concepts
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1)
        coll2 (concepts/create-and-save-concept :collection "PROV1" 1)
        coll1-id (:concept-id coll1)
        coll2-id (:concept-id coll2)
        gran1 (concepts/create-and-save-concept :granule "REG_PROV" coll1 1)
        gran2 (concepts/create-and-save-concept :granule "PROV1" coll2 1)
        ;; A variable has to be associated one and only one collection of the same provider.
        variable1 (concepts/create-and-save-concept :variable "REG_PROV" 1 nil {:coll-concept-id coll1-id})
        variable2 (concepts/create-and-save-concept :variable "PROV1" 2 nil {:coll-concept-id coll2-id})
        var-assn-concept-id1 (get-in variable1 [:variable-association :concept-id])
        var-assn-concept-id2 (get-in variable2 [:variable-association :concept-id])
        variable-association1 (:concept (util/get-concept-by-id var-assn-concept-id1))
        variable-association2 (:concept (util/get-concept-by-id var-assn-concept-id2))]

    ;; Delete REG_PROV
    (util/delete-provider "REG_PROV")

    ;; Verify REG_PROV related concepts are deleted
    (is (concept-deleted? coll1))
    (is (concept-deleted? gran1))
    (is (concept-deleted? variable1))
    ;; variable-association1 is related to the deleted provider.
    (is (concept-deleted? variable-association1))

    ;; Verify concepts not related to the delete provider (REG_PROV) still exist
    (is (concept-exist? coll2))
    (is (concept-exist? gran2))
    (is (concept-exist? variable2))
    ;; variable-association2 is not related to the deleted provider
    (is (concept-exist? variable-association2))))
