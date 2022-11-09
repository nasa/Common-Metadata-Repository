(ns cmr.metadata-db.int-test.concepts.association-save-test
  "Contains integration tests for saving generic associations. Tests saves with various
   configurations including checking for proper error handling."
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer :all]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(deftest save-generic-assoc-data-test
  (testing "Ensure the generic association saved worked with a data in the payload."
    (let [coll-concept (concepts/create-and-save-concept :collection "REG_PROV" 1)
          gen-concept (concepts/create-and-save-concept :data-quality-summary "REG_PROV" 1)
          assoc-concept (concepts/create-concept :generic-association coll-concept gen-concept 1 {:data {:XYZ "ZYX"}})
          saved-assoc (util/save-concept assoc-concept)
          stored-assoc (:concept (util/get-concept-by-id-and-revision (:concept-id saved-assoc)
                                                                      (:revision-id saved-assoc)))
          assoc-metadata (edn/read-string (:metadata stored-assoc))]
      (is (= {:XYZ "ZYX"} (:data assoc-metadata))))))
