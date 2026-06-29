(ns cmr.system-int-test.search.granule.granule-orbit-jruby-bug-test
  "Tests for spatial search with orbital back tracking specifically checking for JRuby array class cast bug."
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest orbit-jruby-classcast-bug
  (testing "Verify that spatial queries using orbits do not throw JRuby ClassCastException"
    (let [coll (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-collection.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "OMSO2-collection"
                                                   :format-key :dif10})
          gran (d/ingest-concept-with-metadata-file "CMR-4722/OMSO2.003-granule.xml"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :concept-id "G1-PROV1"
                                                      :native-id "OMSO2-granule"
                                                      :format-key :echo10})]
      (index/wait-until-indexed)
      
      (let [found (search/find-refs :granule
                                    {:short-name "OMSO2"
                                     :bounding-box "154.7168,13.4223,155.5090,14.1917"
                                     :page-size 200
                                     :page-num 1})]
        (is (not (:errors found)))
        (is (= 0 (:hits found)))))))
