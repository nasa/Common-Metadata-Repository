(ns cmr.metadata-db.int-test.concepts.search.group-search-test
  "Contains integration tests for searching groups."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "REG_PROV" :small false}
                                                 {:provider-id "SMAL_PROV1" :small true}))

(deftest find-groups
  (let [group1 (concepts/create-and-save-concept :access-group "REG_PROV" 1 3)
        group2 (concepts/create-and-save-concept :access-group "CMR" 2 2)]
    (testing "find with parameters"
      (testing "latest revisions"
        (are3 [groups params]
          (is (= (set (map util/expected-concept groups))
                 (set (-> (util/find-latest-concepts :access-group params)
                          :concepts
                          util/concepts-for-comparison))))
          ;; These are the only valid combinations for groups
          "provider-id, native-id"
          [group1] {:provider-id "REG_PROV" :native-id "native-id 1"}

          "no metadata"
          [(dissoc group1 :metadata)] {:provider-id "REG_PROV"
                                       :exclude-metadata true}))

      (testing "all revisions"
        (are3 [rev-count params]
          (= rev-count
             (count (-> (util/find-concepts :access-group params)
                        :concepts)))
          "provider-id, native-id - three revisons"
          3 {:provider-id "REG_PROV" :native-id "native-id 1"}
          "everything"
          5 {})))))
