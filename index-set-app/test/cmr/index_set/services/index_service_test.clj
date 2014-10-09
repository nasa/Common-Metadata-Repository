(ns cmr.index-set.services.index-service-test
  "unit tests for index-set app service functions"
  (:require [clojure.string :as s]
            [clojure.test :refer :all]
            [cmr.index-set.services.index-service :as svc]
            [cmr.system-trace.core :refer [deftracefn]]))

(def sample-index-set
  {:index-set {:name "cmr-base-index-set"
               :id 3
               :create-reason "include message about reasons for creating this index set"
               :collection {:index-names ["C4-collections", "c6_Collections"]
                            :settings {:index {:number_of_shards 1,
                                               :number_of_replicas 0,
                                               :refresh_interval "20s"}}
                            :mapping {:collection {:dynamic "strict",
                                                   :_source {:enabled false},
                                                   :_all {:enabled false},
                                                   :_id   {:path "concept-id"},
                                                   :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                                :entry-title {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}
               :granule {:index-names ["G2-PROV1", "G4-Prov3", "g5_prov5"]
                         :settings {:index {:number_of_shards 1,
                                            :number_of_replicas 0,
                                            :refresh_interval "10s"}}
                         :mapping {:granule {:dynamic "strict",
                                             :_source { "enabled" false},
                                             :_all {"enabled" false},
                                             :_id  {:path "concept-id"},
                                             :properties {:concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                          :collection-concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}}})

(deftest gen-valid-index-name-test
  (let [index-set-id "77"
        req-index-name1 "C4-collections"
        req-index-name2 "G4-Prov3"
        req-index-name3 "g5_prov5"
        expected-index-name1 "77_c4_collections"
        expected-index-name2 "77_g4_prov3"
        expected-index-name3 "77_g5_prov5"
        actual-index-name1 (svc/gen-valid-index-name index-set-id req-index-name1)
        actual-index-name2 (svc/gen-valid-index-name index-set-id req-index-name2)
        actual-index-name3 (svc/gen-valid-index-name index-set-id req-index-name3)]
    (is (= expected-index-name1 actual-index-name1))
    (is (= expected-index-name2 actual-index-name2))
    (is (= expected-index-name3 actual-index-name3))))


(deftest prune-index-set-test
  (let [pruned-index-set {:id 3
                          :name "cmr-base-index-set"
                          :concepts {:collection  {:c6_Collections "3_c6_collections"
                                                   :C4-collections "3_c4_collections"}
                                     :granule {:g5_prov5 "3_g5_prov5"
                                               :G4-Prov3 "3_g4_prov3"
                                               :G2-PROV1 "3_g2_prov1"}}}]
    (is (= (svc/prune-index-set (:index-set sample-index-set)) pruned-index-set))))









