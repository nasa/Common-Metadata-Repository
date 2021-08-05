(ns cmr.system-int-test.ingest.granule-parent-dependency-test
  "CMR granule ingest with validation against parent collection integration tests"
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.test :refer :all]
    [cmr.common.mime-types :as mt]
    [cmr.common.util :as u :refer [are3]]
    [cmr.indexer.system :as indexer-system]
    [cmr.spatial.mbr :as m]
    [cmr.spatial.polygon :as poly]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
    [cmr.system-int-test.utils.humanizer-util :as hu]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.metadata-db-util :as mdb]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm.umm-granule :as umm-g]
    [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"})
                       hu/grant-all-humanizers-fixture
                       hu/save-sample-humanizers-fixture]))

;; Ways in which a granule can refererence a parent collection:
;;
;; collection-ref/entry-title
;; collection-ref/entry-id
;; collection-ref/short-name must match parent collection product/short-name
;; collection-ref/version-id must match parent collection product/version-id
;; product-specific-attributes/name
;; platform-refs/short-name, platform-refs/instrument-refs/short-name
;; project-refs - must be a subset of parent collection projects/short-name
;; spatial-coverage must match parent collection spatial-coverage/granule-spatial-repsresentation
;; temporal - start-date, end-date must be contained in parent-collection start-date, end-date
;; two-d-coordinate-system - start-coordinate-1, end-coordinate-1, start-coordinate-2, end-coordinate-2
;; must fall within bounds defined in parent collection

;; This test demonstrates how granule's platform references the collection's platform and its aliases
;; The platform aliases defined in the humanizer: "AM-1" is the alias for "Terra"
(deftest granule-match-parent-collection-platform-alias-test
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "a-float" :DataType "FLOAT" :ParameterRangeBegin 1.0 :ParameterRangeEnd 10.0})
        gpsa (dg/psa "a-float" [7.0])
        projects (data-umm-cmn/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])
        two-d-cs {:TilingIdentificationSystemName "MISR"
                  :Coordinate1 {:MinimumValue 100
                                :MaximumValue 200}
                  :Coordinate2 {:MinimumValue 300
                                :MaximumValue 400}}
        g-two-d-cs (dg/two-d-coordinate-system
                     {:name "MISR"
                      :start-coordinate-1 110
                      :end-coordinate-1 130
                      :start-coordinate-2 300
                      :end-coordinate-2 328})

        i1 (data-umm-cmn/instrument {:ShortName "instrumentA"})
        ir1 (dg/instrument-ref {:short-name "instrumentA"})
        i2 (data-umm-cmn/instrument {:ShortName "instrumentB"})
        ir2 (dg/instrument-ref {:short-name "instrumentB"})

        c-p1 (data-umm-cmn/platform {:ShortName "Terra" :Instruments [i1]})
        c-p2 (data-umm-cmn/platform {:ShortName "Foo"})
        c-p3 (data-umm-cmn/platform {:ShortName "AM-1" :Instruments [i2]})

        g-pr1 (dg/platform-ref {:short-name "Terra"})
        g-pr2 (dg/platform-ref {:short-name "Terra" :instrument-refs [ir1]})
        g-pr3 (dg/platform-ref {:short-name "Terra" :instrument-refs [ir2]})
        g-pr4 (dg/platform-ref {:short-name "Foo"})
        g-pr5 (dg/platform-ref {:short-name "AM-1"})
        g-pr6 (dg/platform-ref {:short-name "AM-1" :instrument-refs [ir1]})
        g-pr7 (dg/platform-ref {:short-name "AM-1" :instrument-refs [ir2]})
        g-pr8 (dg/platform-ref {:short-name "Bar"})

        coll-data1 {:EntryTitle "short_name1_version"
                    :ShortName "short_name1"
                    :Version "version"
                    :AdditionalAttributes [psa1]
                    :Platforms [c-p1 c-p2]
                    :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                           :ShortName "Larc"})]
                    :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                   :Topic "Cool"
                                                                   :Term "Mild"})]
                    :Projects projects
                    :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                    :TilingIdentificationSystems [two-d-cs]
                    :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                    :TemporalExtents [(data-umm-cmn/temporal-extent
                                       {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                        :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}

        coll-data2 {:EntryTitle "short_name2_version"
                    :ShortName "short_name2"
                    :Version "version"
                    :AdditionalAttributes [psa1]
                    :Platforms[c-p1 c-p3]
                    :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                           :ShortName "Larc"})]
                    :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                   :Topic "Cool"
                                                                   :Term "Mild"})]
                    :Projects projects
                    :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                    :TilingIdentificationSystems [two-d-cs]
                    :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                    :TemporalExtents [(data-umm-cmn/temporal-extent
                                       {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                        :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}

        gran-data1 {:platform-refs [g-pr1]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data2 {:platform-refs [g-pr2]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data3 {:platform-refs [g-pr3]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data4 {:platform-refs [g-pr4]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data5 {:platform-refs [g-pr5]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data6 {:platform-refs [g-pr6]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data7 {:platform-refs [g-pr7]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        gran-data8 {:platform-refs [g-pr8]
                    :spatial-coverage gran-spatial-rep
                    :two-d-coordinate-system g-two-d-cs
                    :product-specific-attributes [gpsa]
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        echo10-coll1 (data-umm-c/collection coll-data1)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll1 {:format :echo10})
        echo10-coll2 (data-umm-c/collection coll-data2)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll2 {:format :echo10})

        gran-Terra-coll1 (dg/granule-with-umm-spec-collection echo10-coll1 (:concept-id echo10-coll1) gran-data1)
        gran-AM-1-coll1 (dg/granule-with-umm-spec-collection echo10-coll1 (:concept-id echo10-coll1) gran-data5)
        gran-Foo-coll1 (dg/granule-with-umm-spec-collection echo10-coll1 (:concept-id echo10-coll1) gran-data4)
        gran-Bar-coll1 (dg/granule-with-umm-spec-collection echo10-coll1 (:concept-id echo10-coll1) gran-data8)
        gran-AM-1-InstrumentA-coll1 (dg/granule-with-umm-spec-collection echo10-coll1 (:concept-id echo10-coll1) gran-data6)

        gran-Terra-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data1)
        gran-AM-1-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data5)
        gran-AM-1-InstrumentA-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data6)
        gran-AM-1-InstrumentB-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data7)
        gran-Terra-InstrumentA-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data2)
        gran-Terra-InstrumentB-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data3)
        gran-Foo-coll2 (dg/granule-with-umm-spec-collection echo10-coll2 (:concept-id echo10-coll2) gran-data4)]

    (are3 [exp-errors gran]
          (is (= exp-errors
                 (flatten (map (fn [error] (:errors error))
                               (:errors (d/ingest "PROV1" gran {:format :echo10 :allow-failure? true}))))))

          "gran-Terra-coll1 success test"
          []
          gran-Terra-coll1

          "gran-AM-1-coll1 success test"
          []
          gran-AM-1-coll1

          "gran-Foo-coll1 success test"
          []
          gran-Foo-coll1

          "gran-AM-1-InstrumentA-coll1 success test"
          []
          gran-AM-1-InstrumentA-coll1

          "gran-Bar-coll1 failure test"
          ["The following list of Platform short names did not exist in the referenced parent collection: [Bar]."]
          gran-Bar-coll1

          "gran-Terra-coll2 success test"
          []
          gran-Terra-coll2

          "gran-AM-1-coll2 success test"
          []
          gran-AM-1-coll2

          "gran-AM-1-InstrumentA-coll2 failure test-platform alias is not added if its shortname exists in the platforms"
          ["The following list of Instrument short names did not exist in the referenced parent collection: [instrumentA]."]
          gran-AM-1-InstrumentA-coll2

          "gran-AM-1-InstrumentB-coll2 success test"
          []
          gran-AM-1-InstrumentB-coll2

          "gran-Terra-InstrumentA-coll2 success test"
          []
          gran-Terra-InstrumentA-coll2

          "gran-Terra-InstrumentB-coll2 failure test"
          ["The following list of Instrument short names did not exist in the referenced parent collection: [instrumentB]."]
          gran-Terra-InstrumentB-coll2

          "gran-Foo-coll2 failure test"
          ["The following list of Platform short names did not exist in the referenced parent collection: [Foo]."]
          gran-Foo-coll2)))

;; This test demonstrates how granule's tile references the collection's tile and its aliases.
;; The tile aliases defined in the humanizer: "SOURCE_TILE" is the alias for "REPLACEMENT_TILE"
(deftest granule-match-parent-collection-tile-alias-test
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "a-float" :DataType "FLOAT" :ParameterRangeBegin 1.0 :ParameterRangeEnd 10.0})
        gpsa (dg/psa "a-float" [7.0])
        i1 (data-umm-cmn/instrument {:ShortName "instrument-Sn A"})
        ir1 (dg/instrument-ref {:short-name "instrument-Sn A"})
        p1 (data-umm-cmn/platform {:ShortName "platform-Sn A" :Instruments [i1]})
        pr1 (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [ir1]})
        projects (data-umm-cmn/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])
        c-two-d-cs-A {:TilingIdentificationSystemName "WRS-1"
                      :Coordinate1 {:MinimumValue 100
                                    :MaximumValue 200}
                      :Coordinate2 {:MinimumValue 300
                                    :MaximumValue 400}}
        c-two-d-cs-B {:TilingIdentificationSystemName "CALIPSO"
                      :Coordinate1 {:MinimumValue 100
                                    :MaximumValue 200}
                      :Coordinate2 {:MinimumValue 300
                                    :MaximumValue 400}}
        g-two-d-cs-A (dg/two-d-coordinate-system
                       {:name "WRS-1"
                        :start-coordinate-1 110
                        :end-coordinate-1 130
                        :start-coordinate-2 300
                        :end-coordinate-2 328})
        g-two-d-cs-B (dg/two-d-coordinate-system
                       {:name "CALIPSO"
                        :start-coordinate-1 110
                        :end-coordinate-1 130
                        :start-coordinate-2 300
                        :end-coordinate-2 328})
        coll-data-A {:EntryTitle "short_name_A_version"
                     :ShortName "short_name_A"
                     :Version "version"
                     :AdditionalAttributes [psa1]
                     :Platforms [p1]
                     :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                            :ShortName "Larc"})]
                     :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                    :Topic "Cool"
                                                                    :Term "Mild"})]
                     :Projects projects
                     :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                     :TilingIdentificationSystems [c-two-d-cs-A]
                     :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                     :TemporalExtents [(data-umm-cmn/temporal-extent
                                        {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                         :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        coll-data-B {:EntryTitle "short_name_B_version"
                     :ShortName "short_name_B"
                     :Version "version"
                     :AdditionalAttributes [psa1]
                     :Platforms [p1]
                     :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                            :ShortName "Larc"})]
                     :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                    :Topic "Cool"
                                                                    :Term "Mild"})]
                     :Projects projects
                     :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                     :TilingIdentificationSystems [c-two-d-cs-B]
                     :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                     :TemporalExtents [(data-umm-cmn/temporal-extent
                                        {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                         :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        gran-data-A {:platform-refs [pr1]
                     :spatial-coverage gran-spatial-rep
                     :two-d-coordinate-system g-two-d-cs-A
                     :product-specific-attributes [gpsa]
                     :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                     :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        gran-data-B {:platform-refs [pr1]
                     :spatial-coverage gran-spatial-rep
                     :two-d-coordinate-system g-two-d-cs-B
                     :product-specific-attributes [gpsa]
                     :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                     :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        echo10-coll-A (data-umm-c/collection coll-data-A)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll-A {:format :echo10})
        echo10-coll-B (data-umm-c/collection coll-data-B)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll-B {:format :echo10})
        gran-A-for-echo10-coll-A (dg/granule-with-umm-spec-collection echo10-coll-A (:concept-id echo10-coll-A) gran-data-A)
        gran-B-for-echo10-coll-A (dg/granule-with-umm-spec-collection echo10-coll-A (:concept-id echo10-coll-A) gran-data-B)
        gran-A-for-echo10-coll-B (dg/granule-with-umm-spec-collection echo10-coll-B (:concept-id echo10-coll-B) gran-data-A)
        gran-B-for-echo10-coll-B (dg/granule-with-umm-spec-collection echo10-coll-B (:concept-id echo10-coll-B) gran-data-B)]

    (are3 [exp-errors gran]
          (is (= exp-errors
                 (flatten (map (fn [error] (:errors error))
                               (:errors (d/ingest "PROV1" gran {:format :echo10 :allow-failure? true}))))))

          "A granule ingested in collection A with OldName is permitted"
          []
          gran-A-for-echo10-coll-A

          "A granule ingested in collection A with NewName is rejected"
          ["The following list of Tiling Identification System Names did not exist in the referenced parent collection: [CALIPSO]."]
          gran-B-for-echo10-coll-A

          "A granule ingested in collection B with different TilingIdentificationSystemName is not permitted"
          ["The following list of Tiling Identification System Names did not exist in the referenced parent collection: [WRS-1]."]
          gran-A-for-echo10-coll-B

          "A granule ingested in collection B with NewName is permitted"
          []
          gran-B-for-echo10-coll-B)))

;; This test demonstrates how granule's instrument references the collection's instrument and its aliases,
;; and how granule's sensor references the collection's sensor and its aliases.
;; Sensors should have instrument aliases applied to them.
;; The instrument aliases defined in the humanizer as following:
;; "source_value": "GPS"
;; "replacement_value": "GPS RECEIVERS"
;; "source_value": "GPS RECEIVERS"
;; "replacement_value": "GPS Receivers"
;; Note: this one also covers tile reference successful cases.
(deftest granule-match-parent-collection-instrument-alias-test
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "a-float" :DataType "FLOAT" :ParameterRangeBegin 1.0 :ParameterRangeEnd 10.0})
        gpsa (dg/psa "a-float" [7.0])
        projects (data-umm-cmn/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])

        sA (data-umm-cmn/instrument {:ShortName "GPS"})
        srA (dg/sensor-ref {:short-name "GPS"})
        iA (data-umm-cmn/instrument {:ShortName "GPS" :ComposedOf [sA]})
        irA (dg/instrument-ref {:short-name "GPS" :sensor-refs [srA]})
        pA (data-umm-cmn/platform {:ShortName "platform-Sn A" :Instruments [iA]})
        prA (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [irA]})

        sB (data-umm-cmn/instrument {:ShortName "GPS RECEIVERS"})
        srB (dg/sensor-ref {:short-name "GPS RECEIVERS"})
        iB (data-umm-cmn/instrument {:ShortName "GPS RECEIVERS" :ComposedOf [sB]})
        irB (dg/instrument-ref {:short-name "GPS RECEIVERS" :sensor-refs [srB]})
        pB (data-umm-cmn/platform {:ShortName "platform-Sn A" :Instruments [iB]})
        prB (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [irB]})

        c-two-d-cs {:TilingIdentificationSystemName "MISR"
                    :Coordinate1 {:MinimumValue 100
                                  :MaximumValue 200}
                    :Coordinate2 {:MinimumValue 300
                                  :MaximumValue 400}}
        g-two-d-cs-A (dg/two-d-coordinate-system
                       {:name "MISR"
                        :start-coordinate-1 110
                        :end-coordinate-1 130
                        :start-coordinate-2 300
                        :end-coordinate-2 328})
        g-two-d-cs-B (dg/two-d-coordinate-system
                       {:name "MISR"
                        :start-coordinate-1 110
                        :end-coordinate-1 130
                        :start-coordinate-2 300
                        :end-coordinate-2 328})
        coll-data-A {:EntryTitle "short_name_A_version"
                     :ShortName "short_name_A"
                     :Version "version"
                     :AdditionalAttributes [psa1]
                     :Platforms [pA]
                     :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                            :ShortName "Larc"})]
                     :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                    :Topic "Cool"
                                                                    :Term "Mild"})]
                     :Projects projects
                     :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                     :TilingIdentificationSystems [c-two-d-cs]
                     :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                     :TemporalExtents [(data-umm-cmn/temporal-extent
                                        {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                         :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        coll-data-B {:EntryTitle "short_name_B_version"
                     :ShortName "short_name_B"
                     :Version "version"
                     :AdditionalAttributes [psa1]
                     :Platforms [pB]
                     :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                            :ShortName "Larc"})]
                     :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                    :Topic "Cool"
                                                                    :Term "Mild"})]
                     :Projects projects
                     :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                     :TilingIdentificationSystems [c-two-d-cs]
                     :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                     :TemporalExtents [(data-umm-cmn/temporal-extent
                                        {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                         :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        gran-data-A {:platform-refs [prA]
                     :spatial-coverage gran-spatial-rep
                     :two-d-coordinate-system g-two-d-cs-A
                     :product-specific-attributes [gpsa]
                     :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                     :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        gran-data-B {:platform-refs [prB]
                     :spatial-coverage gran-spatial-rep
                     :two-d-coordinate-system g-two-d-cs-B
                     :product-specific-attributes [gpsa]
                     :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                     :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        echo10-coll-A (data-umm-c/collection coll-data-A)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll-A {:format :echo10})
        echo10-coll-B (data-umm-c/collection coll-data-B)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll-B {:format :echo10})
        gran-A-for-echo10-coll-A (dg/granule-with-umm-spec-collection echo10-coll-A (:concept-id echo10-coll-A) gran-data-A)
        gran-B-for-echo10-coll-A (dg/granule-with-umm-spec-collection echo10-coll-A (:concept-id echo10-coll-A) gran-data-B)
        gran-A-for-echo10-coll-B (dg/granule-with-umm-spec-collection echo10-coll-B (:concept-id echo10-coll-B) gran-data-A)
        gran-B-for-echo10-coll-B (dg/granule-with-umm-spec-collection echo10-coll-B (:concept-id echo10-coll-B) gran-data-B)]

    (are3 [exp-errors gran]
          (is (= exp-errors
                 (flatten (map (fn [error] (:errors error))
                               (:errors (d/ingest "PROV1" gran {:format :echo10 :allow-failure? true}))))))

          "A granule ingested in collection A with OldName is permitted"
          []
          gran-A-for-echo10-coll-A

          "A granule ingested in collection A with NewName is rejected"
          ["The following list of Instrument short names did not exist in the referenced parent collection: [GPS RECEIVERS]."
           "The following list of Sensor short names did not exist in the referenced parent collection: [GPS RECEIVERS]."]
          gran-B-for-echo10-coll-A

          "A granule ingested in collection B with OldName is permitted"
          []
          gran-A-for-echo10-coll-B

          "A granule ingested in collection B with NewName is permitted"
          []
          gran-B-for-echo10-coll-B)))

;; This tests demonstrates limitations of various collection formats because they do not support
;; fields referenced by a child granule.
(deftest granule-match-parent-collection-test
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "a-float" :DataType "FLOAT" :ParameterRangeBegin 1.0 :ParameterRangeEnd 10.0})
        gpsa (dg/psa "a-float" [7.0])
        i1 (data-umm-cmn/instrument {:ShortName "instrument-Sn A"})
        ir1 (dg/instrument-ref {:short-name "instrument-Sn A"})
        p1 (data-umm-cmn/platform {:ShortName "platform-Sn A" :Instruments [i1]})
        pr1 (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [ir1]})
        projects (data-umm-cmn/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])
        two-d-cs {:TilingIdentificationSystemName "CALIPSO"
                  :Coordinate1 {:MinimumValue 100
                                :MaximumValue 200}
                  :Coordinate2 {:MinimumValue 300
                                :MaximumValue 400}}
        g-two-d-cs (dg/two-d-coordinate-system
                     {:name "CALIPSO"
                      :start-coordinate-1 110
                      :end-coordinate-1 130
                      :start-coordinate-2 300
                      :end-coordinate-2 328})
        coll-data {:EntryTitle "short_name1_version"
                   :ShortName "short_name1"
                   :Version "version"
                   :AdditionalAttributes [psa1]
                   :Platforms [p1]
                   :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                          :ShortName "Larc"})]
                   :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                  :Topic "Cool"
                                                                  :Term "Mild"})]
                   :Projects projects
                   :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                   :TilingIdentificationSystems [two-d-cs]
                   :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                   :TemporalExtents [(data-umm-cmn/temporal-extent
                                      {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                       :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        gran-data {:platform-refs [pr1]
                   :spatial-coverage gran-spatial-rep
                   :two-d-coordinate-system g-two-d-cs
                   :product-specific-attributes [gpsa]
                   :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                   :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        echo10-coll (data-umm-c/collection coll-data)
        _ (d/ingest-umm-spec-collection "PROV1" echo10-coll {:format :echo10})
        dif-coll (data-umm-c/collection (assoc coll-data :EntryTitle "short_name2_version"
                                         :ShortName "short_name2"))
        _ (d/ingest-umm-spec-collection "PROV1" dif-coll {:format :dif})
        dif10-coll (data-umm-c/collection (assoc coll-data :EntryTitle "short_name3_version"
                                           :ShortName "short_name3"))
        _ (d/ingest-umm-spec-collection "PROV1" dif10-coll {:format :dif10})
        iso19115-coll (data-umm-c/collection (assoc coll-data :EntryTitle "short_name4_version"
                                              :ShortName "short_name4"))
        _ (d/ingest-umm-spec-collection "PROV1" iso19115-coll {:format :iso19115})
        iso-smap-coll (data-umm-c/collection (assoc coll-data :EntryTitle "short_name5_version"
                                              :ShortName "short_name5"))
        _ (d/ingest-umm-spec-collection "PROV1" iso-smap-coll {:format :iso-smap})
        gran-for-echo10-coll (dg/granule-with-umm-spec-collection echo10-coll (:concept-id echo10-coll) gran-data)
        gran-for-dif-coll (dg/granule-with-umm-spec-collection dif-coll (:concept-id dif-coll) gran-data)
        gran-for-dif10-coll (dg/granule-with-umm-spec-collection dif10-coll (:concept-id dif-coll) gran-data)
        gran-for-iso19115-coll (dg/granule-with-umm-spec-collection iso19115-coll (:concept-id iso19115-coll) gran-data)
        gran-for-iso-smap-coll (dg/granule-with-umm-spec-collection iso-smap-coll (:concept-id iso-smap-coll) gran-data)]

    (are3 [exp-errors gran]
          (is (= exp-errors
                 (flatten (map (fn [error] (:errors error))
                               (:errors (d/ingest "PROV1" gran {:format :echo10 :allow-failure? true}))))))
          "ECHO10 collection"
          []
          gran-for-echo10-coll

          "DIF collection"
          ["The following list of Tiling Identification System Names did not exist in the referenced parent collection: [CALIPSO]."]
          gran-for-dif-coll

          "DIF10 collection"
          []
          gran-for-dif10-coll


          "ISO19115 collection"
          []
          gran-for-iso19115-coll

          "ISO-SMAP collection"
          ["The following list of Additional Attributes did not exist in the referenced parent collection: [a-float]."
           "[Geometries] cannot be set when the parent collection's GranuleSpatialRepresentation is NO_SPATIAL"]
          gran-for-iso-smap-coll)))

;; This tests for limitations when changing the format for a collection with granules.
;; CMR-2326 - Based on the test above, we would expect to see the same errors seen when saving
;; collections in formats that don't support some things referenced by child granules, but we
;; do not.
(deftest collection-format-change-test
  (let [psa1 (data-umm-cmn/additional-attribute {:Name "a-float" :DataType "FLOAT" :ParameterRangeBegin 1.0 :ParameterRangeEnd 10.0})
        gpsa (dg/psa "a-float" [7.0])
        i1 (data-umm-cmn/instrument {:ShortName "instrument-Sn A"})
        ir1 (dg/instrument-ref {:short-name "instrument-Sn A"})
        p1 (data-umm-cmn/platform {:ShortName "platform-Sn A" :Instruments [i1]})
        pr1 (dg/platform-ref {:short-name "platform-Sn A" :instrument-refs [ir1]})
        projects (data-umm-cmn/projects "proj")
        mbr1 (umm-s/set-coordinate-system :geodetic (m/mbr 10 10 20 0))
        gran-spatial-rep (apply dg/spatial [mbr1])
        two-d-cs {:TilingIdentificationSystemName "MISR"
                  :Coordinate1 {:MinimumValue 100
                                :MaximumValue 200}
                  :Coordinate2 {:MinimumValue 300
                                :MaximumValue 400}}
        g-two-d-cs (dg/two-d-coordinate-system
                     {:name "MISR"
                      :start-coordinate-1 110
                      :end-coordinate-1 130
                      :start-coordinate-2 300
                      :end-coordinate-2 328})
        coll-data {:EntryTitle "short_name1_version"
                   :ShortName "short_name1"
                   :Version "version"
                   :AdditionalAttributes [psa1]
                   :Platforms [p1]
                   :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                          :ShortName "Larc"})]
                   :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                  :Topic "Cool"
                                                                  :Term "Mild"})]
                   :Projects projects
                   :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                   :TilingIdentificationSystems [two-d-cs]
                   :RelatedUrls [(data-umm-cmn/related-url {:Type "PROJECT HOME PAGE" :URL "http://www.foo.com"})]
                   :TemporalExtents [(data-umm-cmn/temporal-extent
                                      {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                       :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}
        gran-data {:platform-refs [pr1]
                   :spatial-coverage gran-spatial-rep
                   :two-d-coordinate-system g-two-d-cs
                   :product-specific-attributes [gpsa]
                   :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                   :ending-date-time "1967-10-12T07:00:00.000-05:00"}
        coll (data-umm-c/collection coll-data)
        _ (d/ingest-umm-spec-collection "PROV1" coll {:format :echo10})
        gran (dg/granule-with-umm-spec-collection coll (:concept-id coll)  gran-data)
        _ (d/ingest "PROV1" gran {:format :echo10 :allow-failure? true})]
    (index/wait-until-indexed)

    (are [exp-errors metadata-format]
         (= exp-errors
            (flatten (:errors (d/ingest-umm-spec-collection "PROV1" coll {:format metadata-format
                                                                          :allow-failure? true}))))
         ["Collection TilingIdentificationSystemName [misr] is referenced by existing granules, cannot be removed. Found 1 granules."]
         :dif

         []
         :dif10

         []
         :iso19115

         ["Collection additional attribute [a-float] is referenced by existing granules, cannot be removed. Found 1 granules."
          "Collection changing from GEODETIC granule spatial representation to NO_SPATIAL is not allowed when the collection has granules. Found 1 granules."]
         :iso-smap)))

(deftest nsidc-iso-collection-echo10-granule
  (testing "granule reference collection via additional attributes and granule spatial representation
           works correctly when the parent collection is in ISO19115 format"
           (let [coll-metadata (slurp (io/resource "iso-samples/nsidc-cmr-3177-iso-collection.xml"))
                 _ (ingest/ingest-concept
                     (ingest/concept :collection "PROV1" "foo" :iso19115 coll-metadata))
                 gran-metadata (slurp (io/resource "iso-samples/nsidc-cmr-3177-granule.xml"))
                 {:keys [status]} (ingest/ingest-concept
                                    (ingest/concept :granule "PROV1" "foo" :echo10 gran-metadata))]
             (is (= 201 status)))))

(deftest iso-smap-collection-echo10-granule
  (testing "granule reference collection via platforms and instruments
           works correctly when the parent collection is in ISO SMAP format"
           (let [coll-metadata (slurp (io/resource "iso-samples/cmr-4125-iso-collection.xml"))
                 _ (ingest/ingest-concept
                     (ingest/concept :collection "PROV1" "foo" :iso-smap coll-metadata))
                 gran-metadata (slurp (io/resource "iso-samples/cmr-4125-granule.xml"))
                 {:keys [status]} (ingest/ingest-concept
                                    (ingest/concept :granule "PROV1" "foo" :echo10 gran-metadata))]
             (is (= 201 status)))))

;; Test specific to an issue ingesting an echo10 granule with a polygon in spatial data with
;; an iso-19115 parent collection with a nil Granule Spatial representation
;; An exception would be seen when ingesting the granule and processing the polygon with a default
;; GSR
(deftest no-spatial-test
  (let [coll-data1 {:EntryTitle "short_name1_version"
                    :ShortName "short_name1"
                    :Version "version"
                    :DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                           :ShortName "Larc"})]
                    :ScienceKeywords [(data-umm-cmn/science-keyword {:Category "upcase"
                                                                   :Topic "Cool"
                                                                   :Term "Mild"})]
                    :SpatialExtent (data-umm-c/spatial {:gsr "NO_SPATIAL"})
                    :TemporalExtents [(data-umm-cmn/temporal-extent
                                       {:beginning-date-time "1965-12-12T07:00:00.000-05:00"
                                        :ending-date-time "1967-12-12T07:00:00.000-05:00"})]}

        gran-data1 {:spatial-coverage
                      (dg/spatial
                        (umm-s/set-coordinate-system
                          :geodetic
                          (poly/polygon
                            [(umm-s/ords->ring 1 1, -1 1, -1 -1, 1 -1, 1 1)
                             (umm-s/ords->ring 0,0, 0.00004,0, 0.00006,0.00005, 0.00002,0.00005, 0,0)])))
                    :beginning-date-time "1966-12-12T07:00:00.000-05:00"
                    :ending-date-time "1967-10-12T07:00:00.000-05:00"}

        coll1 (data-umm-c/collection coll-data1)
        _ (d/ingest-umm-spec-collection "PROV1" coll1 {:format :iso19115})
        gran1 (dg/granule-with-umm-spec-collection coll1 (:concept-id coll1)  gran-data1)
        granule-result (d/ingest "PROV1" gran1 {:format :echo10 :allow-failure? true})]
     ;; The test is checking that the exception does not occur
     ;; 422 status is the expected behavior
     (is (= 422 (:status granule-result)))
     (is (= ["[Geometries] cannot be set when the parent collection's GranuleSpatialRepresentation is NO_SPATIAL"]
            (:errors (first (:errors granule-result)))))))
