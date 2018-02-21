(ns cmr.metadata-db.int-test.concepts.search.granule-search-test
  "Contains integration tests for searching granules"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}))

(deftest find-granules
  (let [coll1 (concepts/create-and-save-concept :collection "REG_PROV" 1 1)
        gran1 (concepts/create-and-save-concept
               :granule "REG_PROV" coll1 1 3 {:native-id "G1-NAT"
                                              :extra-fields {:granule-ur "G1-UR"}})
        gran2 (concepts/create-and-save-concept
               :granule "REG_PROV" coll1 2 2 {:native-id "G2-NAT"
                                              :extra-fields {:granule-ur "G2-UR"}})]
    (testing "find with parameters"
      (testing "latest revsions"
        (are3 [granules params]
          (is (= (set (map util/expected-concept granules))
                 (set (-> (util/find-latest-concepts :granule params)
                          :concepts
                          util/concepts-for-comparison))))
          ;; These are the only valid combinations for granules
          "provider-id, granule-ur"
          [gran1] {:provider-id "REG_PROV" :granule-ur "G1-UR"}

          "provider-id, native-id"
          [gran2] {:provider-id "REG_PROV" :native-id "G2-NAT"}

          "no metadata"
          [(dissoc gran1 :metadata)] {:provider-id "REG_PROV"
                                      :granule-ur "G1-UR"
                                      :exclude-metadata true}))

      (testing "all revisions"
        (are3 [rev-count params]
          (is (= rev-count
                 (count (-> (util/find-concepts :granule params)
                            :concepts))))
          "provider-id, granule-ur - two revisions"
          2 {:provider-id "REG_PROV" :granule-ur "G2-UR"}

          "provider-id, native-id - three revisons"
          3 {:provider-id "REG_PROV":native-id "G1-NAT"})))))

(deftest find-granules-with-invalid-parameters
  (testing "invalid combination"
    (is (= {:status 400
            :errors ["Finding concept type [granule] with parameter combination [provider-id, granule-ur, native-id] is not supported."]}
           (util/find-concepts :granule {:provider-id "REG_PROV"
                                         :granule-ur "GRAN_UR"
                                         :native-id "NV1"})))))
