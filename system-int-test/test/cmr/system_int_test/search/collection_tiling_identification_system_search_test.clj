(ns cmr.system-int-test.search.collection-tiling-identification-system-search-test
  "Integration test for collection two d coordinate name search"
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-by-tiling-identification-system-name
  (let [tiling1 (data-umm-cmn/tiling-identification-system "one CALIPSO")
        tiling2 (data-umm-cmn/tiling-identification-system "two CALIPSO")
        tiling3 (data-umm-cmn/tiling-identification-system "three CALIPSO")
        tiling4 (data-umm-cmn/tiling-identification-system "three Bravo")
        tiling5 (data-umm-cmn/tiling-identification-system "four Bravo")
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:TilingIdentificationSystems [tiling1]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:TilingIdentificationSystems [tiling2]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:TilingIdentificationSystems [tiling3]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:TilingIdentificationSystems [tiling4]}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:TilingIdentificationSystems [tiling2 tiling5]}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {}))]
    (index/wait-until-indexed)

    (testing "tiling idenfitication system search by two_d_coordinate_system_name parameter"
      (are [items tilings options]
           (let [params (merge {"two_d_coordinate_system_name[]" tilings}
                               (when options
                                 {"options[two_d_coordinate_system_name]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           ;; search by by two d coordinate system name - single value
           [coll1] "one CALIPSO" {}

           ;; search by two d coordinate system name - multiple values
           [coll1 coll4] ["one CALIPSO" "three Bravo"] {}

           ;; search by two d coordinate system name - wildcards
           [coll3 coll4] "three *" {:pattern true}
           [] "three *" {:pattern false}
           [] "three *" {}

           ;; search by two d coordinate system name - no match
           [] "NO MATCH" {}

           ;; search by two d coordinate system name - multiple in collection
           [coll2 coll5] "two CALIPSO" {}))

    (testing "two d coordinate search by two_d_coordinate_system[name] parameter"
      (are [items tilings]
           (let [params {"two_d_coordinate_system[name]" tilings}]
             (d/refs-match? items (search/find-refs :collection params)))

           ;; search by by two d coordinate system name - single value
           [coll1] "one CALIPSO"

           ;; search by two d coordinate system name - multiple values
           [coll1 coll4] ["one CALIPSO" "three Bravo"]

           ;; search by two d coordinate system name - no match
           [] "NO MATCH"

           ;; search by two d coordinate system name - multiple in collection
           [coll2 coll5] "two CALIPSO")

      (is (= {:status 400,
              :errors ["two_d_coordinate_system[name] can not be empty"]}
             (search/find-refs :collection {"two_d_coordinate_system[name]" ""})))

      (is (= {:status 400,
              :errors ["two_d_coordinate_system[coordinates] is not supported for collection search."]}
             (search/find-refs :collection {"two_d_coordinate_system[name]" "grid"
                                            "two_d_coordinate_system[coordinates]" "dummy"}))))

    (testing "two d coordinate search by aql"
      (are [items tilings options]
           (let [condition (merge {:TwoDCoordinateSystemName tilings} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           ;; search by by two d coordinate system name - single value
           [coll1] "one CALIPSO" {}

           ;; search by two d coordinate system name - multiple values
           [coll1 coll4] ["one CALIPSO" "three Bravo"] {}

           ;; search by two d coordinate system name - wildcards
           [coll3 coll4] "three %" {:pattern true}

           ;; search by two d coordinate system name - no match
           [] "NO MATCH" {}

           ;; search by two d coordinate system name - multiple in collection
           [coll2 coll5] "two CALIPSO" {}))

    (testing "two d coordinate search using JSON Query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll1] {:two_d_coordinate_system_name "one CALIPSO"}
           [coll1 coll4] {:or [{:two_d_coordinate_system_name "one CALIPSO"}
                               {:two_d_coordinate_system_name "three Bravo"}]}
           [] {:two_d_coordinate_system_name "NO MATCH"}
           [coll2 coll5] {:two_d_coordinate_system_name "two CALIPSO"}
           [coll3 coll4] {:two_d_coordinate_system_name {:value "three *"
                                                         :pattern true
                                                         :ignore_case false}}))))
