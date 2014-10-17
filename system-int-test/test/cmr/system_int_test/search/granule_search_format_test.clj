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
            [cmr.system-int-test.data2.atom-json :as dj]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.kml :as dk]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.ring-relations :as rr]
            [clj-http.client :as client]
            [cmr.common.concepts :as cu]
            [cmr.umm.core :as umm]
            [cmr.umm.spatial :as umm-s]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(comment

  (do
    (ingest/reset)
    (ingest/create-provider "provguid1" "PROV1")
    (ingest/create-provider "provguid2" "PROV2"))

  )

(deftest search-granules-in-xml-metadata
  (let [c1-echo (d/ingest "PROV1" (dc/collection) :echo10)
        c2-smap (d/ingest "PROV2" (dc/collection) :iso-smap)
        g1-echo (d/ingest "PROV1" (dg/granule c1-echo {:granule-ur "g1"
                                                       :producer-gran-id "p1"}) :echo10)
        g2-echo (d/ingest "PROV1" (dg/granule c1-echo {:granule-ur "g2"
                                                       :producer-gran-id "p2"}) :echo10)
        g1-smap (d/ingest "PROV2" (dg/granule c2-smap {:granule-ur "g3"
                                                       :producer-gran-id "p3"}) :iso-smap)
        g2-smap (d/ingest "PROV2" (dg/granule c2-smap {:granule-ur "g4"
                                                       :producer-gran-id "p2"}) :iso-smap)
        all-granules [g1-echo g2-echo g1-smap g2-smap]]
    (index/refresh-elastic-index)

    (testing "Finding refs ingested in different formats"
      (are [search expected]
           (d/refs-match? expected (search/find-refs :granule search))
           {} all-granules
           {:granule-ur "g1"} [g1-echo]
           {:granule-ur "g3"} [g1-smap]
           {:producer-granule-id "p1"} [g1-echo]
           {:producer-granule-id "p3"} [g1-smap]
           {:producer-granule-id "p2"} [g2-echo g2-smap]
           {:granule-ur ["g1" "g4"]} [g1-echo g2-smap]
           {:producer-granule-id ["p1" "p3"]} [g1-echo g1-smap]))

    (testing "Retrieving results in echo10"
      (are [search expected]
           (d/assert-metadata-results-match
             :echo10 expected
             (search/find-metadata :granule :echo10 search))
           {} all-granules
           {:granule-ur "g1"} [g1-echo]
           {:granule-ur "g3"} [g1-smap])

      (testing "as extension"
        (d/assert-metadata-results-match
          :echo10 [g1-echo]
          (search/find-metadata :granule :echo10
                                {:granule-ur "g1"}
                                {:url-extension "echo10"}))))

    (testing "Retrieving results in SMAP ISO format is not supported"
      (is (= {:errors ["The mime type [application/iso:smap+xml] is not supported."],
              :status 400}
             (search/get-search-failure-xml-data
               (search/find-metadata :granule :iso-smap {}))))
      (testing "as extension"
        (is (= {:errors ["The mime type [application/iso:smap+xml] is not supported."],
                :status 400}
               (search/get-search-failure-data
                 (search/find-concepts-in-format
                   nil :granule {} {:url-extension "iso_smap"}))))))

    (testing "Retrieving results in ISO19115"
      (d/assert-metadata-results-match
        :iso19115 all-granules
        (search/find-metadata :granule :iso19115 {}))
      (testing "as extension"
        (are [url-extension]
             (d/assert-metadata-results-match
               :iso19115 [g1-echo]
               (search/find-metadata :granule :iso19115 {:granule-ur "g1"} {:url-extension url-extension}))
             "iso"
             "iso19115")))

    (testing "Retrieving results in a format specified as a comma separated list"
      (are [format-str]
           (d/refs-match?
             [g1-echo]
             (search/parse-reference-response
               false
               (search/find-concepts-in-format
                 format-str
                 :granule
                 {:granule-ur "g1"})))
           "text/html,application/xhtml+xml, application/xml;q=0.9,*/*;q=0.8"
           "text/html, application/xhtml+xml, application/xml;q=0.9,*/*;q=0.8"
           "*/*; q=0.5, application/xml"))

    (testing "invalid format"
      (is (= {:errors ["The mime type [application/echo11+xml] is not supported."],
              :status 400}
             (search/get-search-failure-xml-data
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
        (is (d/refs-match? [g1-echo] refs))

        (testing "Location allows granule native format retrieval"
          (let [response (client/get location
                                     {:accept :application/echo10+xml
                                      :connection-manager (url/conn-mgr)})]
            (is (= (umm/umm->xml g1-echo :echo10) (:body response))))))

      (testing "as extension"
        (is (d/refs-match? [g1-echo] (search/find-refs :granule
                                                       {:granule-ur "g1"}
                                                       {:url-extension "xml"})))))
    (testing "ECHO Compatibility mode"
      (testing "XML References"
        (are [refs]
             (and (d/echo-compatible-refs-match? all-granules refs)
                  (= "array" (:type refs)))
             (search/find-refs :granule {:echo-compatible true})
             (search/find-refs-with-aql :granule [] [] {:query-params {:echo_compatible true}})))

      (testing "ECHO10"
        (d/assert-echo-compatible-metadata-results-match
          :echo10 all-granules
          (search/find-metadata :granule :echo10 {:echo-compatible true}))))))


(deftest search-granule-csv
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :beginning-date-time "2010-01-01T12:00:00Z"
                                                   :ending-date-time "2010-01-11T12:00:00Z"
                                                   :producer-gran-id "Granule #1"
                                                   :day-night "DAY"
                                                   :size 100
                                                   :cloud-cover 50
                                                   :related-urls [ru1 ru2 ru3]}))
        gran2 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule2"
                                                   :beginning-date-time "2011-01-01T12:00:00Z"
                                                   :ending-date-time "2011-01-11T12:00:00Z"
                                                   :producer-gran-id "Granule #2"
                                                   :day-night "NIGHT"
                                                   :size 80
                                                   :cloud-cover 30
                                                   :related-urls [ru1]}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule3"
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
                                                 {:url-extension "csv"})
                          [:status :body]))))))

(deftest search-granule-atom-and-json-and-kml
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        ru4 (dc/related-url "ALGORITHM INFO" "http://inherited.com")
        ru5 (dc/related-url "GET RELATED VISUALIZATION" "http://inherited.com/browse")
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :spatial-coverage (dc/spatial {:gsr :geodetic})}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :related-urls [ru4 ru5]}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "OrbitDataset"
                                                :spatial-coverage (dc/spatial {:gsr :orbit
                                                                               :orbit {:inclination-angle 98.0
                                                                                       :period 97.87
                                                                                       :swath-width 390.0
                                                                                       :start-circular-latitude -90.0
                                                                                       :number-of-orbits 1.0}})}))

        make-gran (fn [coll attribs]
                    (d/ingest "PROV1" (dg/granule coll attribs)))

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes  (umm-s/set-coordinate-system :geodetic (poly/polygon [outer hole1 hole2]))

        gran1 (make-gran coll1 {:granule-ur "Granule1"
                                :beginning-date-time "2010-01-01T12:00:00Z"
                                :ending-date-time "2010-01-11T12:00:00Z"
                                :producer-gran-id "Granule #1"
                                :day-night "DAY"
                                :size 100.0
                                :cloud-cover 50.0
                                :orbit-calculated-spatial-domains [{:orbital-model-name "MODEL NAME"
                                                                    :orbit-number 2
                                                                    :start-orbit-number 3.0
                                                                    :stop-orbit-number 4.0
                                                                    :equator-crossing-longitude -45.0
                                                                    :equator-crossing-date-time "2011-01-01T12:00:00.000Z"}]
                                :related-urls [ru1 ru2]
                                :orbit-parameters {:inclination-angle 98.0
                                                   :period 97.87
                                                   :swath-width 390.0
                                                   :start-circular-latitude -90.0
                                                   :number-of-orbits 1.0}
                                :spatial-coverage (dg/spatial
                                                    (poly/polygon
                                                      :geodetic
                                                      [(rr/ords->ring :geodetic -70 20, 70 20, 70 30, -70 30, -70 20)])
                                                    polygon-with-holes
                                                    (p/point 1 2)
                                                    (p/point -179.9 89.4)
                                                    (l/ords->line-string :geodetic 0 0, 0 1, 0 -90, 180 0)
                                                    (l/ords->line-string :geodetic 1 2, 3 4, 5 6, 7 8)
                                                    (m/mbr -180 90 180 -90)
                                                    (m/mbr -10 20 30 -40))})
        gran2 (make-gran coll2 {:granule-ur "Granule2"
                                :beginning-date-time "2011-01-01T12:00:00Z"
                                :ending-date-time "2011-01-11T12:00:00Z"
                                :producer-gran-id "Granule #2"
                                :day-night "NIGHT"
                                :size 80.0
                                :cloud-cover 30.0
                                :related-urls [ru3]})
        gran3 (make-gran coll3 {:granule-ur "OrbitGranule"
                                :beginning-date-time "2011-01-01T12:00:00Z"
                                :ending-date-time "2011-01-01T14:00:00Z"
                                :producer-gran-id "OrbitGranuleId"
                                :day-night "NIGHT"
                                :size 80.0
                                :cloud-cover 30.0
                                :related-urls [ru3]
                                :spatial-coverage (dg/spatial (dg/orbit 120.0 50.0 :asc 50.0 :asc))
                                :orbit-calculated-spatial-domains
                                [{:orbital-model-name "MODEL NAME"
                                  :start-orbit-number 3.0
                                  :stop-orbit-number 4.0
                                  :equator-crossing-longitude -45.0
                                  :equator-crossing-date-time "2011-01-01T12:00:00.000Z"}]})
        gran4 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule4"}) :iso-smap)]

    (index/refresh-elastic-index)

    (testing "kml"
      (let [results (search/find-concepts-kml :granule {})]
        (dk/assert-granule-kml-results-match
          [gran1 gran2 gran3 gran4] [coll1 coll2 coll3 coll1] results)))

    (testing "atom"
      (let [coll-atom (da/collections->expected-atom [coll1] "collections.atom?entry_title=Dataset1")
            response (search/find-concepts-atom :collection {:entry-title "Dataset1"})]
        (is (= 200 (:status response)))
        (is (= coll-atom
               (:results response))))
      (let [gran-atom (da/granules->expected-atom [gran1] [coll1] "granules.atom?granule_ur=Granule1")
            response (search/find-concepts-atom :granule {:granule-ur "Granule1"})]
        (is (= 200 (:status response)))
        (is (= gran-atom
               (:results response))))
      (let [gran-atom (da/granules->expected-atom
                        [gran1 gran2 gran3 gran4] [coll1 coll2 coll3 coll1] "granules.atom")
            response (search/find-concepts-atom :granule {})]
        (is (= 200 (:status response)))
        (is (= gran-atom
               (:results response))))
      (let [gran-atom (da/granules->expected-atom [gran3] [coll3] "granules.atom?granule_ur=OrbitGranule")
            response (search/find-concepts-atom :granule {:granule-ur "OrbitGranule"})]
        (is (= 200 (:status response)))
        (is (= gran-atom
               (:results response))))

      (testing "empty results"
        (let [gran-atom (da/granules->expected-atom [] [] "granules.atom?granule_ur=foo")
              response (search/find-concepts-atom :granule {:granule-ur "foo"})]
          (is (= 200 (:status response)))
          (is (= gran-atom
                 (:results response)))))

      (testing "as extension"
        (is (= (select-keys
                 (search/find-concepts-atom :granule {:granule-ur "Granule1"})
                 [:status :results])
               (select-keys
                 (search/find-concepts-atom :granule
                                            {:granule-ur "Granule1"}
                                            {:url-extension "atom"})
                 [:status :results])))))

    (testing "json"
      (let [gran-json (dj/granules->expected-json [gran1] [coll1] "granules.json?granule_ur=Granule1")
            response (search/find-concepts-json :granule {:granule-ur "Granule1"})]
        (is (= 200 (:status response)))
        (is (= gran-json
               (:results response))))

      (let [gran-json (dj/granules->expected-json
                        [gran1 gran2 gran3 gran4] [coll1 coll2 coll3 coll1] "granules.json")
            response (search/find-concepts-json :granule {})]
        (is (= 200 (:status response)))
        (is (= gran-json
               (:results response))))

      (testing "as extension"
        (is (= (select-keys
                 (search/find-concepts-json :granule {:granule-ur "Granule1"})
                 [:status :results])
               (select-keys
                 (search/find-concepts-json :granule
                                            {:granule-ur "Granule1"}
                                            {:url-extension "json"})
                 [:status :results])))))))
