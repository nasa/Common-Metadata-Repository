(ns cmr.system-int-test.search.granule-grid-search-test
  "Integration test for CMR granule grid search"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-granule-orbit-number
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:ShortName "S1"
                                                                            :Version "V1"
                                                                            :EntryTitle "E1"
                                                                            :TilingIdentificationSystems
                                                [{:TilingIdentificationSystemName "CALIPSO"
                                                  :Coordinate1 {:MinimumValue 100
                                                                :MaximumValue 200}
                                                  :Coordinate2 {:MinimumValue 300
                                                                :MaximumValue 400}}]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:ShortName "S2"
                                                                            :Version "V2"
                                                                            :EntryTitle "E2"
                                                                            :TilingIdentificationSystems
                                                [{:TilingIdentificationSystemName "MISR"
                                                  :Coordinate1 {:MinimumValue 100
                                                                :MaximumValue 200}
                                                  :Coordinate2 {:MinimumValue 300
                                                                :MaximumValue 400}}]}))
        gran1 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 300
                                                             :end-coordinate-2 328})}))
        gran2 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 320
                                                             :end-coordinate-2 340})}))
        gran3 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 150
                                                             :end-coordinate-1 170
                                                             :start-coordinate-2 328
                                                             :end-coordinate-2 361})}))
        gran4 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 150
                                                             :end-coordinate-1 170
                                                             :start-coordinate-2 330
                                                             :end-coordinate-2 340})}))
        gran5 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 350
                                                             :end-coordinate-2 370})}))
        gran6 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 361
                                                             :end-coordinate-2 370})}))
        gran7 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 328})}))
        gran8 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 331})}))
        gran9 (d/ingest "PROV1"
                        (dg/granule-with-umm-spec-collection
                          coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                            {:name "CALIPSO"
                                                             :start-coordinate-1 110
                                                             :end-coordinate-1 130
                                                             :start-coordinate-2 361})}))
        gran10 (d/ingest "PROV1"
                         (dg/granule-with-umm-spec-collection
                           coll1 (:concept-id coll1) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                             {:name "CALIPSO"
                                                              :start-coordinate-1 110
                                                              :end-coordinate-1 130
                                                              :start-coordinate-2 329
                                                              :end-coordinate-2 360.0})}))
        gran11 (d/ingest "PROV1"
                         (dg/granule-with-umm-spec-collection
                           coll2 (:concept-id coll2) {:two-d-coordinate-system (dg/two-d-coordinate-system
                                                             {:name "MISR"
                                                              :start-coordinate-1 100
                                                              :end-coordinate-1 120
                                                              :start-coordinate-2 300
                                                              :end-coordinate-2 320})}))
        gran12 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1) {}))]
    (index/wait-until-indexed)

    (testing "search granules by grid"
      (are [items two-d]
           (d/refs-match? items (search/find-refs :granule {"grid[]" two-d}))

           ;; search by grid name
           [gran11] "MISR"
           ;; search by grid value
           [gran1] "CALIPSO:110,300"
           ;; search by grid range
           [gran3] "CALIPSO:160-170,350-360"
           [gran2 gran3 gran4, gran5 gran8 gran10] "CALIPSO:,329-360"
           [gran1 gran2 gran3 gran4 gran5 gran7 gran8 gran10] "CALIPSO:,-360"
           [gran3 gran5 gran6 gran9 gran10] "CALIPSO:,360-"
           ;; search by multiple grid ranges
           [gran3 gran5 gran6 gran9 gran10] "CALIPSO:160-170,350-360:,360-"
           [gran3 gran4] "CALIPSO:160-170,350-360:150-160,330-340"
           [gran3 gran4 gran5 gran6 gran9 gran10] "CALIPSO:160-170,350-360:150-160,330-340:,360-"
           ;; search by multiple grids
           [gran3 gran11] ["CALIPSO:160-170,350-360" "MISR:100,310"]))

    (testing "invalid grid parameters"
      (are [two-d error]
           (let [{:keys [status errors]} (search/find-refs :granule {:grid two-d})]
             (is (= [400 [error]]
                    [status errors])))
           " :1," "Grid name can not be empty, but is for [ :1,]"
           "name:x" "Grid values [x] must be numeric value or range"
           "name:10-x" "Grid values [10-x] must be numeric value or range"
           "name:10-5" "Invalid grid range for [10-5]"
           "name:6,10-5" "Invalid grid range for [10-5]"))

    (testing "catalog-rest style"
      (are [items two-d-name coords]
           (let [search-params {"two_d_coordinate_system[name]" two-d-name}
                 search-params (if coords
                                 (merge search-params
                                        {"two_d_coordinate_system[coordinates]" coords})
                                 search-params)]
             (d/refs-match? items (search/find-refs :granule search-params)))
           ;; search by grid name
           [gran11] "MISR" nil
           ;; search by grid value
           [gran1] "CALIPSO" "110-110:300-300"
           ;; search by grid range
           [gran3] "CALIPSO" "160-170:350-360"
           [gran2 gran3 gran4, gran5 gran8 gran10] "CALIPSO" "-:329-360"
           [gran1 gran2 gran3 gran4 gran5 gran7 gran8 gran10] "CALIPSO" "-:-360"
           [gran3 gran5 gran6 gran9 gran10] "CALIPSO" "-:360-"
           ;; search by multiple grid ranges
           [gran3 gran5 gran6 gran9 gran10] "CALIPSO" "160-170:350-360,-:360-"
           [gran3 gran4] "CALIPSO" "160-170:350-360,150-160:330-340"
           [gran3 gran4 gran5 gran6 gran9 gran10]
           "CALIPSO" "160-170:350-360,150-160:330-340,-:360-"))

    (testing "invalid catalog-rest two-d-coordinate-system parameters"
      (are [two-d-name coords error]
           (let [search-params {"two_d_coordinate_system[name]" two-d-name}
                 search-params (if coords
                                 (merge search-params
                                        {"two_d_coordinate_system[coordinates]" coords})
                                 search-params)
                 {:keys [status errors]} (search/find-refs :granule search-params)]
             (is (= [400 [error]]
                    [status errors])))
           " " "1" "Grid name can not be empty, but is for [ :1]"
           "name" "x" "Grid values [x] must be numeric value or range"
           "name" "10-x" "Grid values [10-x] must be numeric value or range"
           "name" "10-5" "Invalid grid range for [10-5]"
           "name" "6,10-5" "Invalid grid range for [10-5]"))

    (testing "search by two d coordinate system with aql"
      (are [items two-d]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule
                                                     [{:TwoDCoordinateSystem two-d}]))
           ;; search by grid name
           [gran11] {:name "MISR" :coord-1 nil :coord-2 nil}
           [] {:name "misr" :coord-1 nil :coord-2 nil}
           ;; ignore-case
           [] {:name "misr" :coord-1 nil :coord-2 nil :ignore-case false}
           [gran11] {:name "MISR" :coord-1 nil :coord-2 nil :ignore-case true}
           [gran11] {:name "MISR" :coord-1 nil :coord-2 nil :ignore-case false}
           [gran11] {:name "MISR" :coord-1 nil :coord-2 nil :ignore-case true}
           ;; search by grid value
           [gran1] {:name "CALIPSO" :coord-1 110 :coord-2 300}
           ;; search by grid range
           [gran3] {:name "CALIPSO" :coord-1 [160 170] :coord-2 [350 360]}
           [gran2 gran3 gran4, gran5 gran8 gran10]
           {:name "CALIPSO" :coord-1 nil :coord-2 [329 360]}
           [gran1 gran2 gran3 gran4 gran5 gran7 gran8 gran10]
           {:name "CALIPSO" :coord-1 nil :coord-2 [nil 360]}
           [gran3 gran5 gran6 gran9 gran10] {:name "CALIPSO" :coord-1 nil :coord-2 [360 nil]}))

    (testing "invalid AQL two-d-coordinate-system"
      (are [two-d-name coord-1 coord-2 error]
           (let [two-d {:name two-d-name :coord-1 coord-1 :coord-2 coord-2}
                 {:keys [status errors]} (search/find-refs-with-aql
                                           :granule
                                           [{:TwoDCoordinateSystem two-d}])]
             (is (= [400 [error]]
                    [status errors])))
           " " "1" nil "TwoDCoordinateSystemName can not be empty"
           "name" "x" nil "Invalid format for coordinate1, it should be numeric"
           "name" nil "x" "Invalid format for coordinate2, it should be numeric"
           "name" [10 "x"] nil "Invalid format for coordinate1, it should be numeric"
           "name" [5 1] nil
           "TwoDCoordinateSystem coordinate1 range lower [5.0] should be smaller than upper [1.0]"
           "name" nil [5 1]
           "TwoDCoordinateSystem coordinate2 range lower [5.0] should be smaller than upper [1.0]"))))
