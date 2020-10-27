(ns cmr.system-int-test.search.collection-with-new-granule-search-test
  "Integration test for searching collections created after a given date. These tests are to ensure
   proper CMR Harvesting functionality.

   Note that we can only perform these tests with the in-memory database because with Oracle we use
   the Oracle database server time for setting created-at and revision-date. With the in-memory
   database we are able to use timekeeper so we can set the dates to the values we want."
  (:require
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util]
   [cmr.spatial.mbr :as mbr]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
   [cmr.system-int-test.utils.dev-system-util :as dev-system-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.umm-spec.models.umm-common-models :as umm-cmn]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]))

(use-fixtures :each (join-fixtures
                      [(ingest/reset-fixture {"provguid1" "PROV1"
                                              "provguid2" "PROV2"})
                       (dev-system-util/freeze-resume-time-fixture)]))

(defn- create-test-collections-and-granules
  "Separated out the test setup into a separate function."
  []
  (let [coll-w-may-2010-granule (d/ingest-umm-spec-collection
                                 "PROV1"
                                 (data-umm-c/collection
                                   {:EntryTitle "may2010"
                                    :ShortName "may2010"}))
        _ (dev-system-util/freeze-time! "2010-05-01T10:00:00Z")
        may-2010-granule (d/ingest "PROV1"
                                  (dg/granule-with-umm-spec-collection
                                    coll-w-may-2010-granule (:concept-id coll-w-may-2010-granule)))
        coll-w-may-2015-granule (d/ingest-umm-spec-collection
                                 "PROV2"
                                 (data-umm-c/collection
                                   {:EntryTitle "may2015"
                                    :ShortName "Regular"}))
        _ (dev-system-util/freeze-time! "2015-05-01T10:00:00Z")
        may-2015-granule (d/ingest "PROV2"
                                   (dg/granule-with-umm-spec-collection
                                     coll-w-may-2015-granule (:concept-id coll-w-may-2015-granule)))
        coll-w-june-2016-granule (d/ingest-umm-spec-collection
                                       "PROV1"
                                       (data-umm-c/collection
                                         {:EntryTitle "june2016"
                                          :ShortName "june2016"}))
        _ (dev-system-util/freeze-time! "2016-06-07T10:00:00Z")
        june-2016-granule (d/ingest "PROV1"
                                    (dg/granule-with-umm-spec-collection
                                      coll-w-june-2016-granule
                                      (:concept-id coll-w-june-2016-granule)))
        coll-prov2-w-june-2016-granule (d/ingest-umm-spec-collection
                                         "PROV2"
                                         (data-umm-c/collection
                                           {:EntryTitle "june2016"
                                            :ShortName "june2016"}))
        prov2-june-2016-granule (d/ingest "PROV2"
                                          (dg/granule-with-umm-spec-collection
                                            coll-prov2-w-june-2016-granule
                                            (:concept-id coll-prov2-w-june-2016-granule)))
        coll-with-deleted-granule (d/ingest-umm-spec-collection
                                    "PROV1"
                                    (data-umm-c/collection
                                      {:EntryTitle "deletedgranule"
                                       :ShortName "deletedgranule"}))
        deleted-granule-revision-1 (d/ingest "PROV1"
                                             (dg/granule-with-umm-spec-collection
                                              coll-with-deleted-granule
                                              (:concept-id coll-with-deleted-granule)))

        delete-granule-params {:provider-id (:provider-id deleted-granule-revision-1)
                               :native-id (:granule-ur deleted-granule-revision-1)
                               :concept-type :granule}

        deleted-granule-revision-2 (ingest/delete-concept delete-granule-params)

        coll-temporal-match (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection
                              {:EntryTitle "temporalmatch"
                               :ShortName "temporalmatch"
                               :TemporalExtents
                               [(data-umm-cmn/temporal-extent
                                 {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        coll-temporal-no-match (d/ingest-umm-spec-collection
                                 "PROV1"
                                 (data-umm-c/collection
                                  {:EntryTitle "notemporalmatch"
                                   :ShrotName "notemporalmatch"
                                   :TemporalExtents
                                   [(data-umm-cmn/temporal-extent
                                     {:beginning-date-time "1970-01-01T00:00:00Z"})]}))

        _ (dev-system-util/freeze-time! "2011-06-07T10:00:00Z")
        gran-temporal-match (d/ingest "PROV1" (dg/granule-with-umm-spec-collection
                                               coll-temporal-match
                                               (:concept-id coll-temporal-match)
                                               {:beginning-date-time "2010-12-12T12:00:00Z"
                                                :ending-date-time "2011-01-03T12:00:00Z"}))

        gran-no-temporal-match (d/ingest "PROV1" (dg/granule-with-umm-spec-collection
                                                  coll-temporal-no-match
                                                  (:concept-id coll-temporal-no-match)
                                                  {:beginning-date-time "2000-12-12T12:00:00Z"
                                                   :ending-date-time "2001-01-03T12:00:00Z"}))
        hsd {:Geometry (umm-c/map->GeometryType
                        {:CoordinateSystem "GEODETIC"
                         :BoundingRectangles [(umm-c/map->BoundingRectangleType
                                               {:WestBoundingCoordinate -180
                                                :NorthBoundingCoordinate 90
                                                :EastBoundingCoordinate 180
                                                :SouthBoundingCoordinate -90})]})}
        coll-spatial-match (d/ingest-umm-spec-collection
                            "PROV1" (data-umm-c/collection
                                     {:EntryTitle "collspatialmatch"
                                      :ShortName "collspatialmatch"
                                      :Version "1"
                                      :SpatialExtent (data-umm-c/spatial
                                                      {:gsr "GEODETIC"
                                                       :sr "GEODETIC"
                                                       :hsd hsd})}))
        gran-spatial-match (d/ingest "PROV1" (dg/granule-with-umm-spec-collection
                                              coll-spatial-match
                                              (:concept-id coll-spatial-match)
                                              {:spatial-coverage (dg/spatial (mbr/mbr 45 90 55 70))}))
        coll-archive-center-match (d/ingest-umm-spec-collection
                                   "PROV2"
                                   (data-umm-c/collection
                                     {:EntryTitle "coll-archive-center-match"
                                      :ShortName "coll-archive-center-match"
                                      :DataCenters [(data-umm-cmn/data-center
                                                     {:Roles ["ARCHIVER"]
                                                      :ShortName "NSIDC"})]}))
        gran-archive-center-match (d/ingest "PROV2"
                                            (dg/granule-with-umm-spec-collection
                                             coll-archive-center-match
                                             (:concept-id coll-archive-center-match)))
        coll-platform-match (d/ingest-umm-spec-collection
                             "PROV1"
                             (data-umm-c/collection {:Platforms [(data-umm-cmn/platform
                                                                  {:ShortName "AQUA"})]
                                                     :EntryTitle "coll-platform-match"
                                                     :ShortName "coll-platform-match"}))
        _ (dev-system-util/freeze-time! "2016-06-07T10:00:00Z")
        gran-platform-match (d/ingest "PROV1"
                                      (dg/granule-with-umm-spec-collection
                                       coll-platform-match
                                       (:concept-id coll-platform-match)
                                       {:platform-refs [(dg/platform-ref {:short-name "AQUA"})]}))
        _ (dev-system-util/freeze-time! "2011-06-07T10:00:00Z")
        gran-platform-no-match (d/ingest "PROV1"
                                      (dg/granule-with-umm-spec-collection
                                       coll-platform-match
                                       (:concept-id coll-platform-match)))]


    ;; Sanity check deletion
    (is (= 2 (:revision-id deleted-granule-revision-2)))
    (index/wait-until-indexed)
    (s/only-with-real-database
      ;; Force coll2 granules into their own index to make sure
      ;; granules outside of 1_small_collections get searched properly.
      (bootstrap/start-rebalance-collection (:concept-id coll-w-may-2015-granule))
      (bootstrap/finalize-rebalance-collection (:concept-id coll-w-may-2015-granule))
      (index/wait-until-indexed))
    {:coll-w-may-2010-granule coll-w-may-2010-granule
     :coll-w-may-2015-granule coll-w-may-2015-granule
     :coll-w-june-2016-granule coll-w-june-2016-granule
     :coll-prov2-w-june-2016-granule coll-prov2-w-june-2016-granule
     :coll-temporal-match coll-temporal-match
     :coll-temporal-no-match coll-temporal-no-match
     :coll-archive-center-match coll-archive-center-match
     :coll-spatial-match coll-spatial-match
     :coll-platform-match coll-platform-match}))

(deftest ^:in-memory-db collections-has-granules-created-at-test
  (s/only-with-in-memory-database
    (let [{:keys [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule
                  coll-prov2-w-june-2016-granule coll-temporal-match coll-temporal-no-match
                  coll-spatial-match coll-archive-center-match coll-platform-match]}
          (create-test-collections-and-granules)]
      (testing "has_granules_created_at parameter by itself"
        (util/are3
          [date-ranges expected-results]
          (let [actual-results (search/find-refs :collection {:has-granules-created-at date-ranges})]
            (d/assert-refs-match expected-results actual-results))

          "Single date range"
          ["2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"] [coll-w-may-2015-granule]

          "Prior to date"
          [",2015-06-01T16:13:12Z"] [coll-w-may-2010-granule coll-w-may-2015-granule
                                     coll-temporal-match coll-temporal-no-match coll-spatial-match
                                     coll-archive-center-match coll-platform-match]

          "After date"
          ["2015-06-01T16:13:12Z,"] [coll-w-june-2016-granule coll-prov2-w-june-2016-granule
                                     coll-platform-match]

          "Multiple time ranges"
          [",2014-07-01T16:13:12Z"
           "2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"
           "2016-04-01T10:10:00Z,2016-07-01T16:13:12Z"]
          [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule
           coll-prov2-w-june-2016-granule coll-temporal-match coll-temporal-no-match
           coll-spatial-match coll-archive-center-match coll-platform-match]

          "No matches"
          [",2090-01-01T12:34:56ZZ"] []))

      (testing "Parameters are correctly passed to the granule query"
        (util/are3
          [params expected-results]
          (let [date-range ["2015-06-01T16:13:12Z,"]
                actual-results (search/find-refs
                                 :collection
                                 (merge {:has-granules-created-at date-range} params))]
            (d/assert-refs-match expected-results actual-results))

          "Provider ID"
          {:provider "PROV2"} [coll-prov2-w-june-2016-granule]

          "Collection concept-id"
          {:concept-id (:concept-id coll-w-june-2016-granule)} [coll-w-june-2016-granule]

          "Collection concept-id with no matching granule in time range for that concept-id"
          {:concept-id (:concept-id coll-w-june-2016-granule)
           :has-granules-created-at [",2015-06-01T16:13:12Z"]}
          []

          "Short name"
          {:short-name "coll-platform-match"} [coll-platform-match]

          "Version"
          {:version "1"
           :has-granules-created-at [",2011-06-07T13:00:00Z"]}
          [coll-spatial-match]

          "Entry title case insensitive"
          {:entry-title "COLL-PLATFORM-match"
           "options[entry-title][ignore-case]" "true"}
          [coll-platform-match]

          "Entry title case-sensitive"
          {:entry-title "COLL-PLATFORM-match"
           "options[entry-title][ignore-case]" "false"}
          []

          "Temporal"
          {:temporal ["2010-12-25T12:00:00Z,2011-01-01T12:00:00Z"]
           :has-granules-created-at [",2011-06-07T13:00:00Z"]}
          [coll-temporal-match]

          "Spatial"
          {:bounding-box "0,85,180,85"
           :has-granules-created-at [",2011-06-07T13:00:00Z"]}
          [coll-spatial-match]

          "Spatial does not match granule but matches collection"
          {:bounding-box "-20,0,-15,1"
           :has-granules-created-at [",2011-06-07T13:00:00Z"]}
          []

          "Platform"
          {:platform "AQUA"} [coll-platform-match]

          (str "Platform that matches collection, has matching granules for the time range, but a "
               "different granule platform does not match")
          {:platform "AQUA"
           :has-granules-created-at [",2011-06-07T13:00:00Z"]}
          []))


      (testing "Other collection specific parameters are applied"
        (util/are3
          [params expected-results]
          (let [date-range ["2015-06-01T16:13:12Z," ",2011-06-07T13:00:00Z"]
                actual-results (search/find-refs
                                 :collection
                                 (merge {:has-granules-created-at date-range} params))]
            (d/assert-refs-match expected-results actual-results))

          "Archive center"
          {:archive-center "NSIDC"} [coll-archive-center-match]

          "Keyword"
          {:keyword "PROV1"} [coll-temporal-match coll-temporal-no-match
                              coll-spatial-match coll-w-june-2016-granule
                              coll-w-may-2010-granule coll-platform-match]))

      (testing "has_granules_created_at success in multiple formats with sort"
        (doseq [format [:atom :dif :dif10 :echo10 :iso19115 :json :opendata :xml :umm-json]]
          (testing (str "format: " format)
            (let [results (search/search-concept-ids-in-format
                            format
                            :collection
                            {:has-granules-created-at [",2015-06-01T16:13:12Z"]
                             :sort-key "entry_title"})
                  ;; expected results sorted by entry title
                  expected-results [coll-archive-center-match
                                    coll-platform-match
                                    coll-spatial-match
                                    coll-w-may-2010-granule
                                    coll-w-may-2015-granule
                                    coll-temporal-no-match
                                    coll-temporal-match]]
              (is (= (mapv :concept-id expected-results)
                     results)))))))))


(deftest ^:in-memory-db collection-has-granules-revised-at-test
  (s/only-with-in-memory-database
    (let [{:keys [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule
                  coll-prov2-w-june-2016-granule coll-temporal-match coll-temporal-no-match
                  coll-spatial-match coll-archive-center-match coll-platform-match]}
          (create-test-collections-and-granules)
          _ (dev-system-util/freeze-time! "2017-05-01T10:00:00Z")
          may-2010-granule-rev (d/ingest "PROV1"
                                         (dg/granule-with-umm-spec-collection
                                           coll-w-may-2010-granule
                                           (:concept-id coll-w-may-2010-granule)
                                           {:revision-id 2}))]
      (index/wait-until-indexed)
      (testing "has_granules_revised_at parameter by itself"
        (util/are3
          [date-ranges expected-results]
          (let [actual-results (search/find-refs :collection {:has-granules-revised-at date-ranges})]
            (d/assert-refs-match expected-results actual-results))

          "Single date range"
          ["2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"] [coll-w-may-2015-granule]

          "Prior to date"
          [",2015-06-01T16:13:12Z"] [coll-w-may-2010-granule coll-w-may-2015-granule
                                     coll-temporal-match coll-temporal-no-match coll-spatial-match
                                     coll-archive-center-match coll-platform-match]

          "After date"
          ["2017-04-01T10:00:00Z,"] [coll-w-may-2010-granule]

          "Multiple time ranges"
          [",2014-07-01T16:13:12Z"
           "2015-04-01T10:10:00Z,2015-06-01T16:13:12Z"
           "2016-04-01T10:10:00Z,2016-07-01T16:13:12Z"]
          [coll-w-may-2010-granule coll-w-may-2015-granule coll-w-june-2016-granule
           coll-prov2-w-june-2016-granule coll-temporal-match coll-temporal-no-match
           coll-spatial-match coll-archive-center-match coll-platform-match]

          "No matches"
          [",2090-01-01T12:34:56ZZ"] []))

      (testing "has_granules_revised_at success in multiple formats with sort"
        (doseq [format [:atom :dif :dif10 :echo10 :iso19115 :json :opendata :xml :umm-json]]
          (testing (str "format: " format)
            (let [results (search/search-concept-ids-in-format
                            format
                            :collection
                            {:has-granules-revised-at [",2015-06-01T16:13:12Z"]
                             :sort-key "entry_title"})
                  ;; expected results sorted by entry title
                  expected-results [coll-archive-center-match
                                    coll-platform-match
                                    coll-spatial-match
                                    coll-w-may-2010-granule
                                    coll-w-may-2015-granule
                                    coll-temporal-no-match
                                    coll-temporal-match]]
              (is (= (mapv :concept-id expected-results)
                     results)))))))))
