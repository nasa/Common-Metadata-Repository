(ns cmr.system-int-test.search.granule-search-with-json-query-test
  "Integration test for CMR granule search"
  (:require
   [clojure.string :as s]
   [clojure.test :refer :all]
   [cmr.common-app.services.search.messages :as cmsg]
   [cmr.common-app.services.search.messages :as vmsg]
   [cmr.common.services.messages :as msg]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.services.messages.common-messages :as smsg]
   [cmr.spatial.mbr :as m]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as int-s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm.umm-spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-granules-by-json-query
  (testing "searching granules by json query for"
    (let [s1 (data-umm-cmn/instrument {:ShortName "sensor-1"})
          i1 (data-umm-cmn/instrument {:ShortName "instrument-1" :ComposedOf [s1]})
          p1 (data-umm-cmn/platform {:ShortName "platform-1" :Instruments [i1]})

          coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "OneCollectionV1"
                                                                              :ShortName "S1"
                                                                              :Version "V1"
                                                                              :Platforms [p1]
                                                                              :TemporalExtents
                                                                              [(data-umm-cmn/temporal-extent
                                                                                {:beginning-date-time "2012-01-01T00:00:00Z"
                                                                                 :ending-date-time "2012-02-14T12:00:00Z"})]}))
          coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "AnotherCollectionV1"
                                                                              :ShortName "S2"
                                                                              :Version "V2"
                                                                              :Projects (data-umm-cmn/projects "ABC" "KLM" "XYZ")}))
          coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "OneCollectionV1"
                                                                              :ShortName "S3"
                                                                              :Version "V3"
                                                                              :TilingIdentificationSystems
                                                                              [{:TilingIdentificationSystemName "CALIPSO"
                                                                                :Coordinate1 {:MinimumValue 100
                                                                                              :MaximumValue 200}
                                                                                :Coordinate2 {:MinimumValue 300
                                                                                              :MaximumValue 400}}]}))
          coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "OtherCollectionV1"
                                                                              :ShortName "S4"
                                                                              :Version "V4"
                                                                              :SpatialExtent (data-umm-c/spatial
                                                                                              {:gsr "GEODETIC"})}))

          coll1-cid (get-in coll1 [:concept-id])
          coll2-cid (get-in coll2 [:concept-id])
          coll3-cid (get-in coll3 [:concept-id])
          coll4-cid (get-in coll4 [:concept-id])

          sr1 (dg/sensor-ref {:short-name "sensor-1"})
          ir1 (dg/instrument-ref {:short-name "instrument-1" :sensor-refs [sr1]})
          pr1 (dg/platform-ref {:short-name "platform-1" :instrument-refs [ir1]})

          gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule1"
                                                                                        :platform-refs [pr1]}))
          gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule2"
                                                                                        :beginning-date-time "2012-01-01T12:00:00Z"
                                                                                        :ending-date-time "2012-01-11T12:00:00Z"}))
          gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule3"
                                                                                        :project-refs ["ABC"]}))
          gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll3 coll3-cid {:granule-ur "Granule4"
                                                                                        :two-d-coordinate-system
                                                                                        (dg/two-d-coordinate-system
                                                                                         {:name "CALIPSO"
                                                                                          :start-coordinate-1 110
                                                                                          :end-coordinate-1 130
                                                                                          :start-coordinate-2 300
                                                                                          :end-coordinate-2 328})}))
          gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll4 coll4-cid {:granule-ur "Granule5"
                                                                                        :spatial-coverage
                                                                                        (apply dg/spatial
                                                                                               (map (partial umm-s/set-coordinate-system :geodetic)
                                                                                                    [(m/mbr 10 10 20 0)]))}))]
      (index/wait-until-indexed)

      (testing "all granule validation error"
        (let [resp (search/find-refs-with-json-query :granule {} {:updated_since "2012-01-01T12:00:00Z"})]
          (is (= 400 (:status resp)))
          (is (= ["The CMR does not allow querying across granules with json queries. You should limit your query using conditions that identify one or more collections such as provider, concept_id, short_name, or entry_title."] (:errors resp)))))

      (are3 [items json-query options]
        (d/refs-match? items (search/find-refs-with-json-query :granule options json-query))

        ;; String based conditions
        "single entry-title"
        [gran3]
        {:entry_title "AnotherCollectionV1"}
        {}

        "entry-title with pattern"
        [gran1 gran2 gran4 gran5]
        {:entry_title {:value "O*" :pattern true}}
        {}

        "entry-title and ignore case"
        [gran3]
        {:entry_title {:value "anotherCollectionV1" :ignore_case true}}
        {}

        "short-name"
        [gran1 gran2]
        {:short_name "S1"}
        {}

        "version"
        [gran5]
        {:version "V4"}
        {}

        "provider"
        [gran1 gran2 gran3]
        {:provider "PROV1"}
        {}

        "concept-id"
        [gran1]
        {:concept_id (:concept-id gran1)}
        {}

        "collection-concept-id"
        [gran1 gran2]
        {:collection_concept_id coll1-cid}
        {}

        "project"
        [gran3]
        {:and [{:provider "PROV1"}
               {:project "ABC"}]}
        {}

        "platform"
        [gran1]
        {:and [{:provider "PROV1"}
               {:platform "platform-1"}]}
        {}

        "instrument"
        [gran1]
        {:and [{:provider "PROV1"}
               {:instrument "instrument-1"}]}
        {}

        ;; Spatial
        "bounding box"
        [gran5]
        {:and [{:bounding_box [0 10 10 20]}
               {:provider "PROV2"}]}
        {}

        ;; Temporal or date based condtions
        "temporal"
        [gran2]
        {:and [{:provider "PROV1"}
               {:temporal {:start_date "2012-01-01T12:00:00Z"
                           :end_date "2012-01-11T12:00:00Z"}}]}
        {}

        "updated-since"
        [gran1 gran2 gran3]
        {:and [{:provider "PROV1"}
               {:updated_since "2012-01-01T12:00:00Z"}]}
        {}

        ;; Logic operators
        "not"
        [gran1 gran2 gran3 gran4 gran5]
        {:not
         {:and [{:provider "PROV2"}
                {:instrument "instrument-1"}]}}
        {}

        "or"
        [gran1 gran5]
        {:or [{:instrument "instrument-1"}
              {:version "V4"}]}
        {}

        "and"
        [gran1]
        {:and [{:short_name "S1"}
               {:instrument "instrument-1"}]}
        {}))))
