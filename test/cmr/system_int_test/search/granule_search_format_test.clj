(ns cmr.system-int-test.search.granule-search-format-test
  "Integration tests for searching granules in csv format"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line :as l]
            [cmr.spatial.ring :as r]
            [clj-http.client :as client]
            [cmr.common.concepts :as cu]
            [cmr.umm.core :as umm]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

(comment

  (ingest/reset)
  (ingest/create-provider "CMR_PROV1")

  )

(deftest search-granules-in-xml-metadata
  ;; TODO we can add additional formats here later such as iso
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection))
        coll2 (d/ingest "CMR_PROV1" (dc/collection))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "g1"}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "g2"}))
        all-granules [gran1 gran2]]
    (index/refresh-elastic-index)
    (testing "echo10"
      (d/assert-metadata-results-match
        :echo10 all-granules
        (search/find-metadata :granule :echo10 {}))
      (d/assert-metadata-results-match
        :echo10 [gran1]
        (search/find-metadata :granule :echo10 {:granule-ur "g1"}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :echo10 [gran1]
          (search/find-metadata :granule :echo10
                                {:granule-ur "g1"}
                                {:format-as-ext? true}))))

    (testing "invalid format"
      (is (= {:errors ["The mime type [application/echo11+xml] is not supported."],
              :status 400}
             (search/get-search-failure-data
               (search/find-concepts-in-format
                 "application/echo11+xml" :granule {})))))

    (testing "invalid extension"
      (is (= {:errors ["The URL extension [echo11] is not supported."],
              :status 400}
             (search/get-search-failure-data
               (client/get (str (url/search-url :granule) ".echo11")
                           {:connection-manager (url/conn-mgr)})))))

    (testing "reference XML"
      (let [refs (search/find-refs :granule {:granule-ur "g1"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [gran1] refs))

        (testing "Location allows granule native format retrieval"
          (let [response (client/get location
                                     {:accept :xml
                                      :connection-manager (url/conn-mgr)})]
            (is (= (umm/umm->xml gran1 :echo10) (:body response))))))

      (testing "as extension"
        (is (d/refs-match? [gran1] (search/find-refs :granule
                                                     {:granule-ur "g1"}
                                                     {:format-as-ext? true})))))))


(deftest search-granule-csv
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :beginning-date-time "2010-01-01T12:00:00Z"
                                                       :ending-date-time "2010-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #1"
                                                       :day-night "DAY"
                                                       :size 100
                                                       :cloud-cover 50
                                                       :related-urls [ru1 ru2 ru3]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"
                                                       :beginning-date-time "2011-01-01T12:00:00Z"
                                                       :ending-date-time "2011-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #2"
                                                       :day-night "NIGHT"
                                                       :size 80
                                                       :cloud-cover 30
                                                       :related-urls [ru1]}))
        gran3 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule3"
                                                       :beginning-date-time "2012-01-01T12:00:00Z"
                                                       :ending-date-time "2012-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #3"
                                                       :day-night "NIGHT"
                                                       :size 80
                                                       :cloud-cover 30}))]

    (index/refresh-elastic-index)

    (let [response (search/find-grans-csv :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,\"http://example.com,http://example2.com\",http://example.com/browse,50.0,DAY,100.0\n")
             (:body response))))
    (let [response (search/find-grans-csv :granule {:granule-ur "Granule2"})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule2,Granule #2,2011-01-01T12:00:00Z,2011-01-11T12:00:00Z,http://example.com,,30.0,NIGHT,80.0\n")
             (:body response))))
    (let [response (search/find-grans-csv :granule {})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,\"http://example.com,http://example2.com\",http://example.com/browse,50.0,DAY,100.0\n"
                  "Granule2,Granule #2,2011-01-01T12:00:00Z,2011-01-11T12:00:00Z,http://example.com,,30.0,NIGHT,80.0\n"
                  "Granule3,Granule #3,2012-01-01T12:00:00Z,2012-01-11T12:00:00Z,,,30.0,NIGHT,80.0\n")
             (:body response))))

    (testing "as extension"
      (is (= (select-keys (search/find-grans-csv :granule {:granule-ur "Granule1"})
                          [:status :body])
             (select-keys (search/find-grans-csv :granule
                                                 {:granule-ur "Granule1"}
                                                 {:format-as-ext? true})
                          [:status :body]))))))

(deftest search-granule-atom-and-json
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        ru4 (dc/related-url "ALGORITHM INFO" "http://inherited.com")
        ru5 (dc/related-url "GET RELATED VISUALIZATION" "http://inherited.com/browse")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset1"
                                                    :spatial-coverage (dc/spatial :geodetic)}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset2"
                                                    :related-urls [ru4 ru5]}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :beginning-date-time "2010-01-01T12:00:00Z"
                                                       :ending-date-time "2010-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #1"
                                                       :day-night "DAY"
                                                       :size 100.0
                                                       :cloud-cover 50.0
                                                       :related-urls [ru1 ru2]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"
                                                       :beginning-date-time "2011-01-01T12:00:00Z"
                                                       :ending-date-time "2011-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #2"
                                                       :day-night "NIGHT"
                                                       :size 80.0
                                                       :cloud-cover 30.0
                                                       :related-urls [ru3]
                                                       :spatial-coverage (dc/spatial :geodetic)}))
        make-gran (fn [coll attribs]
                    (d/ingest "CMR_PROV1" (dg/granule coll attribs)))

        ;; polygon with holes
        outer (r/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (r/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (r/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (poly/polygon [outer hole1 hole2])

        gran1 (make-gran coll1 {:granule-ur "Granule1"
                                :beginning-date-time "2010-01-01T12:00:00Z"
                                :ending-date-time "2010-01-11T12:00:00Z"
                                :producer-gran-id "Granule #1"
                                :day-night "DAY"
                                :size 100.0
                                :cloud-cover 50.0
                                :related-urls [ru1 ru2]
                                :spatial-coverage (dg/spatial
                                                    (poly/polygon [(r/ords->ring -70 20, 70 20, 70 30, -70 30, -70 20)])
                                                    polygon-with-holes
                                                    (p/point 1 2)
                                                    (p/point -179.9 89.4)
                                                    (l/ords->line 0 0, 0 1, 0 -90, 180 0)
                                                    (l/ords->line 1 2, 3 4, 5 6, 7 8)
                                                    (m/mbr -180 90 180 -90)
                                                    (m/mbr -10 20 30 -40))})
        gran2 (make-gran coll2 {:granule-ur "Granule2"
                                :beginning-date-time "2011-01-01T12:00:00Z"
                                :ending-date-time "2011-01-11T12:00:00Z"
                                :producer-gran-id "Granule #2"
                                :day-night "NIGHT"
                                :size 80.0
                                :cloud-cover 30.0
                                :related-urls [ru3]})]

    (index/refresh-elastic-index)

    (let [gran-atom (da/granules->expected-atom [gran1] [coll1] "granules.atom?granule-ur=Granule1")
          response (search/find-grans-atom :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= gran-atom
             (:results response))))

    (let [gran-atom (da/granules->expected-atom [gran1 gran2] [coll1 coll2] "granules.atom")
          response (search/find-grans-atom :granule {})]
      (is (= 200 (:status response)))
      (is (= gran-atom
             (:results response))))

    (testing "as extension"
      (is (= (select-keys
               (search/find-grans-atom :granule {:granule-ur "Granule1"})
               [:status :results])
             (select-keys
               (search/find-grans-atom :granule
                                       {:granule-ur "Granule1"}
                                       {:format-as-ext? true})
               [:status :results]))))

    ;; search json format
    (let [gran-json (da/granules->expected-atom [gran1] [coll1] "granules.json?granule-ur=Granule1")
          response (search/find-grans-json :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= gran-json
             (:results response))))

    (let [gran-json (da/granules->expected-atom [gran1 gran2] [coll1 coll2] "granules.json")
          response (search/find-grans-json :granule {})]
      (is (= 200 (:status response)))
      (is (= gran-json
             (:results response))))

    (testing "as extension"
      (is (= (select-keys
               (search/find-grans-json :granule {:granule-ur "Granule1"})
               [:status :results])
             (select-keys
               (search/find-grans-json :granule
                                       {:granule-ur "Granule1"}
                                       {:format-as-ext? true})
               [:status :results]))))))
