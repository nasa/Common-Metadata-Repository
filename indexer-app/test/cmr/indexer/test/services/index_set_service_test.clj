(ns cmr.indexer.test.services.index-set-service-test
  "unit tests for index-set app service functions"
  (:require
   [clojure.test :refer :all]
   [cmr.elastic-utils.config :as es-config]
   [cmr.indexer.data.index-set-generics :as index-set-gen]
   [cmr.indexer.services.index-set-service :as svc]
   [cmr.indexer.test.utility :as util]))

(deftest gen-valid-index-name-test
  (let [index-set-id "77"
        req-index-name1 "C4-collections"
        req-index-name2 "C4-Prov3"
        req-index-name3 "C5_prov5"
        expected-index-name1 "77_c4_collections"
        expected-index-name2 "77_c4_prov3"
        expected-index-name3 "77_c5_prov5"
        actual-index-name1 (svc/gen-valid-index-name index-set-id req-index-name1)
        actual-index-name2 (svc/gen-valid-index-name index-set-id req-index-name2)
        actual-index-name3 (svc/gen-valid-index-name index-set-id req-index-name3)]
    (is (= expected-index-name1 actual-index-name1))
    (is (= expected-index-name2 actual-index-name2))
    (is (= expected-index-name3 actual-index-name3))))

(deftest prune-index-set-test
  (let [expected-pruned-gran-index-set {:id 3
                                        :name "cmr-base-index-set"
                                        :concepts (merge
                                                    {:granule {:small_collections "3_small_collections"
                                                               :C4-PROV3 "3_c4_prov3"
                                                               :C5-PROV5 "3_c5_prov5"}
                                                     :deleted-granule {}})}
        expected-pruned-non-gran-index-set {:id 3
                                            :name "cmr-base-index-set"
                                            :concepts (merge
                                                        {:collection  {:C6-PROV3 "3_c6_prov3"
                                                                       :C4-PROV2 "3_c4_prov2"}
                                                         :tag {}
                                                         :variable {}
                                                         :service {}
                                                         :tool {}
                                                         :autocomplete {}
                                                         :subscription {}}
                                                        (zipmap (keys (index-set-gen/generic-mappings-generator)) (repeat {})))}
        actual-pruned-non-gran-index-set (svc/prune-index-set (:index-set util/sample-index-set) es-config/elastic-name)
        actual-pruned-gran-index-set (svc/prune-index-set (:index-set util/sample-index-set) es-config/gran-elastic-name)]
    (is (= expected-pruned-gran-index-set actual-pruned-gran-index-set))
    (is (= expected-pruned-non-gran-index-set actual-pruned-non-gran-index-set))))
