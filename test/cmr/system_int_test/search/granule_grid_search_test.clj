(ns cmr.system-int-test.search.granule-grid-search-test
  "Integration test for CMR granule grid search"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-granule-orbit-number
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 300
                                                             :end-coordinate-2 328})}))
        gran2 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 320
                                                             :end-coordinate-2 340})}))
        gran3 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 150
                                                             :end-coordinate-1 170
                                                             :start-coordinate-2 328
                                                             :end-coordinate-2 361})}))
        gran4 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 150
                                                             :end-coordinate-1 170
                                                             :start-coordinate-2 330
                                                             :end-coordinate-2 340})}))
        gran5 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 350
                                                             :end-coordinate-2 370})}))
        gran6 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 361
                                                             :end-coordinate-2 370})}))
        gran7 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 328})}))
        gran8 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 331})}))
        gran9 (d/ingest "PROV1"
                        (dg/granule
                          coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "one CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 361})}))
        gran10 (d/ingest "PROV1"
                         (dg/granule
                           coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                             {:name "one CALIPSO"
                                                              :start-coordinate-1 110
                                                              :end-coordinate-1 130
                                                              :start-coordinate-2 329
                                                              :end-coordinate-2 360.0})}))
        gran11 (d/ingest "PROV1"
                         (dg/granule
                           coll1 {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                             {:name "BRAVO"
                                                              :start-coordinate-1 100
                                                              :end-coordinate-1 120
                                                              :start-coordinate-2 300
                                                              :end-coordinate-2 320})}))
        gran12 (d/ingest "PROV1" (dg/granule coll1 {}))]
    (index/refresh-elastic-index)

    (testing "search granules by grid"
      (are [items two-d]
           (d/refs-match? items (search/find-refs :granule {"grid[]" two-d}))

           ;; search by grid name
           [gran11] "BRAVO"
           ;; search by grid value
           [gran1] "one CALIPSO:110,300"
           ;; search by grid range
           [gran3] "one CALIPSO:160-170,350-360"
           [gran2 gran3 gran4, gran5 gran8 gran10] "one CALIPSO:,329-360"
           [gran1 gran2 gran3 gran4 gran5 gran7 gran8 gran10] "one CALIPSO:,-360"
           [gran3 gran5 gran6 gran9 gran10] "one CALIPSO:,360-"
           ;; search by multiple grid ranges
           [gran3 gran5 gran6 gran9 gran10] "one CALIPSO:160-170,350-360:,360-"
           [gran3 gran4] "one CALIPSO:160-170,350-360:150-160,330-340"
           [gran3 gran4 gran5 gran6 gran9 gran10] "one CALIPSO:160-170,350-360:150-160,330-340:,360-"
           ;; search by multiple grids
           [gran3 gran11] ["one CALIPSO:160-170,350-360" "BRAVO:100,310"]))

    (testing "catalog-rest style"
      (are [items two-d-name coords]
           (let [search-params {"two_d_coordinate_system[name]" two-d-name}
                 search-params (if coords
                                 (merge search-params
                                        {"two_d_coordinate_system[coordinates]" coords})
                                 search-params)]
             (d/refs-match? items (search/find-refs
                                    :granule search-params)))
           ;; search by grid name
           [gran11] "BRAVO" nil
           ;; search by grid value
           [gran1] "one CALIPSO" "110-110:300-300"
           ;; search by grid range
           [gran3] "one CALIPSO" "160-170:350-360"
           [gran2 gran3 gran4, gran5 gran8 gran10] "one CALIPSO" "-:329-360"
           [gran1 gran2 gran3 gran4 gran5 gran7 gran8 gran10] "one CALIPSO" "-:-360"
           [gran3 gran5 gran6 gran9 gran10] "one CALIPSO" "-:360-"
           ;; search by multiple grid ranges
           [gran3 gran5 gran6 gran9 gran10] "one CALIPSO" "160-170:350-360,-:360-"
           [gran3 gran4] "one CALIPSO" "160-170:350-360,150-160:330-340"
           [gran3 gran4 gran5 gran6 gran9 gran10]
           "one CALIPSO" "160-170:350-360,150-160:330-340,-:360-"))))

