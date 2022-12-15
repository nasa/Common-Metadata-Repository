(ns cmr.metadata-db.int-test.concepts.search.association-search-test
  "Contains integration tests for searching associations."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "PROV1" :small false}
                                                 {:provider-id "PROV2" :small false}))

(deftest find-associations
  ;; create-and-save-concept the first number is a unique number for probably the native id
  ;; and the second determines the number of the last revision id. So a 2 means insert the concept twice.
  (let [coll1 (concepts/create-and-save-concept
               :collection "PROV1" 1 1 {:extra-fields {:entry-id "entry-1" 
                                                       :entry-title "et1"
                                                       :version-id "v1"
                                                       :short-name "tl1"}})
        coll1-concept-id (:concept-id coll1)
        coll2 (concepts/create-and-save-concept
               :collection "PROV1" 2 1 {:extra-fields {:entry-id "entry-2"
                                                       :entry-title "et2"
                                                       :version-id "v1"
                                                       :short-name "tl2"}})
        associated-service (concepts/create-and-save-concept :service "PROV1" 1)
        associated-tool (concepts/create-and-save-concept :tool "PROV1" 1)
        associated-dqs1 (concepts/create-and-save-concept :data-quality-summary "PROV1" 1)
        associated-dqs2 (concepts/create-and-save-concept :data-quality-summary "PROV2" 2)
        associated-oo (concepts/create-and-save-concept :order-option "PROV1" 1)
        associated-variable1 (concepts/create-and-save-concept
                              :variable "PROV1" 1 1 {:coll-concept-id coll1-concept-id})
        associated-variable2 (concepts/create-and-save-concept
                              :variable "PROV1" 2 1 {:coll-concept-id coll1-concept-id})
        var-to-var (concepts/create-and-save-concept :generic-association associated-variable1 associated-variable2 1 1)
        tool-to-service (concepts/create-and-save-concept :generic-association associated-service associated-tool 1 1)
        associated-tag (concepts/create-and-save-concept :tag "CMR" 1)
        serv-association (concepts/create-and-save-concept :service-association coll1 associated-service 1 2) 
        tool-association1 (concepts/create-and-save-concept :tool-association coll1 associated-tool 1 1)
        dqs-association1 (concepts/create-and-save-concept :generic-association coll1 associated-dqs1 1 1)
        oo-association1 (concepts/create-and-save-concept :generic-association coll1 associated-oo 1 2)
        oo-association2 (concepts/create-and-save-concept :generic-association associated-dqs1 associated-oo 1 1)
        dqs-association2 (concepts/create-and-save-concept :generic-association associated-dqs1 coll2 1 1)]
        
    (testing "find associations by concept-id."
      (are3 [assoc-count params]
            (is (= assoc-count (count (:concepts (util/find-associations params)))))
            
            "all associations"
            8
            {:associated-concept-id coll1-concept-id :source-concept-identifier coll1-concept-id}
            
            "latest associations"
            6
            {:associated-concept-id coll1-concept-id :source-concept-identifier coll1-concept-id :latest true}
            
            "by non existant concept-id"
            0
            {:associated-concept-id "C12XXX-PROV1" :source-concept-identifier "C12XXX-PROV1" :latest true}))))
