(ns cmr.metadata-db.int-test.delete-provider-cascading-test
  "Tests that delete a provider cascading delete its concepts."
  (:require
   [clojure.test :refer :all]
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
  (let [coll1 (util/create-and-save-collection "REG_PROV" 1)
        coll2 (util/create-and-save-collection "PROV1" 1)
        gran1 (util/create-and-save-granule "REG_PROV" coll1 1)
        gran2 (util/create-and-save-granule "PROV1" coll2 1)
        variable1 (util/create-and-save-variable "REG_PROV" 1)
        variable2 (util/create-and-save-variable "PROV1" 1)
        variable-association1 (util/create-and-save-variable-association coll1 variable1 1)
        variable-association2 (util/create-and-save-variable-association coll2 variable2 1)]

    ;; Delete REG_PROV
    (util/delete-provider "REG_PROV")

    ;; Verify the REG_PROV's concepts are deleted
    (is (concept-deleted? coll1))
    (is (concept-deleted? gran1))
    (is (concept-deleted? variable1))
    (is (concept-deleted? variable-association1))

    ;; Verify the PROV1's concepts still exist
    (is (concept-exist? coll2))
    (is (concept-exist? gran2))
    (is (concept-exist? variable2))
    (is (concept-exist? variable-association2))))
