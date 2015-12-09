(ns cmr.system-int-test.search.collection-search-format-test
  "This tests ingesting and searching for collections in different formats."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.atom :as da]
            [cmr.system-int-test.data2.atom-json :as dj]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.system-int-test.system :as s]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [cmr.umm.core :as umm]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.codec :as codec]
            [cmr.umm.spatial :as umm-s]
            [clojure.data.xml :as x]
            [cmr.system-int-test.utils.fast-xml :as fx]
            [cmr.common.util :as util]
            [cmr.common.xml :as cx]
            [cmr.system-int-test.data2.kml :as dk]
            [cmr.system-int-test.data2.opendata :as od]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(use-fixtures :each (ingest/reset-fixture
                      {"provguid1" "PROV1" "provguid2" "PROV2" "usgsguid" "USGS_EROS"}))

(comment

  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
    (ingest/create-provider {:provider-guid "provguid2" :provider-id "PROV2"})

    (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                      :version-id "V1"
                                      :entry-title "ET1"})))

  (def c10-umm-json (d/ingest "PROV1"
                              cmr.umm-spec.test.expected-conversion/example-record
                              {:format :umm-json
                               :accept-format :json})))



;; Tests that we can ingest and find items in different formats
(deftest multi-format-search-test
  (let [
        c1-echo (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                                  :version-id "V1"
                                                  :entry-title "ET1"})
                          {:format :echo10})
        c2-echo (d/ingest "PROV2" (dc/collection {:short-name "S2"
                                                  :version-id "V2"
                                                  :entry-title "ET2"})
                          {:format :echo10})
        c3-dif (d/ingest "PROV1" (dc/collection-dif {:entry-id "S3"
                                                     :short-name "S3"
                                                     :version-id "V3"
                                                     :entry-title "ET3"
                                                     :long-name "ET3"})
                         {:format :dif})
        c4-dif (d/ingest "PROV2" (dc/collection-dif {:entry-id "S4"
                                                     :short-name "S4"
                                                     :version-id "V4"
                                                     :entry-title "ET4"
                                                     :long-name "ET4"})
                         {:format :dif})
        c5-iso (d/ingest "PROV1" (dc/collection {:short-name "S5"
                                                 :entry-id "S5"
                                                 :version-id "V50"})
                         {:format :iso19115})
        c6-iso (d/ingest "PROV2" (dc/collection {:short-name "S6"
                                                 :entry-id "S6"
                                                 :version-id "V6"})
                         {:format :iso19115})
        c7-smap (d/ingest "PROV1" (dc/collection-smap {:short-name "S7"
                                                       :version-id "V7"})
                          {:format :iso-smap})
        c8-dif10 (d/ingest "PROV1" (dc/collection-dif10 {:short-name "S8"
                                                         :version-id "V8"
                                                         :entry-title "ET8"
                                                         :long-name "ET8"})
                           {:format :dif10})
        c9-dif10 (d/ingest "PROV2" (dc/collection-dif10 {:short-name "S9"
                                                         :version-id "V9"
                                                         :entry-title "ET9"
                                                         :long-name "ET9"})
                           {:format :dif10})

        c10-umm-json (d/ingest "PROV1"
                               cmr.umm-spec.test.expected-conversion/example-record
                               {:format :umm-json
                                :accept-format :json})

        all-colls [c1-echo c2-echo c3-dif c4-dif c5-iso c6-iso c7-smap c8-dif10 c9-dif10 c10-umm-json]]
    (index/wait-until-indexed)

    (testing "Finding refs ingested in different formats"
      (are [search expected]
           (d/refs-match? expected (search/find-refs :collection search))
           {} all-colls
           {:short-name "S4"} [c4-dif]
           {:entry-title "ET3"} [c3-dif]
           {:version ["V3" "V2"]} [c2-echo c3-dif]
           {:short-name "S5"} [c5-iso]
           {:short-name "S6"} [c6-iso]
           {:version "V50"} [c5-iso]
           {:version ["V50" "V6"]} [c5-iso c6-iso]
           {:short-name "S7"} [c7-smap]
           {:version "V7"} [c7-smap]
           {:short-name "S8"} [c8-dif10]
           {:version "V9"} [c9-dif10]))

    (testing "Retrieving results in native format"
      ;; Native format for search can be specified using Accept header application/metadata+xml
      ;; or the .native extension.
      (util/are2 [concepts format-key extension accept]
                 (let [params {:concept-id (map :concept-id concepts)}
                       options (-> {:accept nil}
                                   (merge (when extension {:url-extension extension}))
                                   (merge (when accept {:accept accept})))
                       response (search/find-metadata :collection format-key params options)]
                   (d/assert-metadata-results-match format-key concepts response))
                 "ECHO10 .native extension" [c1-echo c2-echo] :echo10 "native" nil
                 "DIF .native extension" [c3-dif c4-dif] :dif "native" nil
                 "ISO MENDS .native extension" [c5-iso c6-iso] :iso19115 "native" nil
                 "SMAP ISO .native extension" [c7-smap] :iso-smap "native" nil
                 "ECHO10 accept application/metadata+xml" [c1-echo c2-echo] :echo10 nil "application/metadata+xml"
                 "DIF accept application/metadata+xml" [c3-dif c4-dif] :dif nil "application/metadata+xml"
                 "ISO MENDS accept application/metadata+xml" [c5-iso c6-iso] :iso19115 nil "application/metadata+xml"
                 "SMAP ISO accept application/metadata+xml" [c7-smap] :iso-smap nil "application/metadata+xml"))

    (testing "Retrieving results in echo10"
      (d/assert-metadata-results-match
        :echo10 all-colls
        (search/find-metadata :collection :echo10 {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :echo10 all-colls
          (search/find-metadata :collection :echo10 {} {:url-extension "echo10"}))))

    (testing "Retrieving results in dif"
      (d/assert-metadata-results-match
        :dif all-colls
        (search/find-metadata :collection :dif {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :dif all-colls
          (search/find-metadata :collection :dif {} {:url-extension "dif"}))))

    (testing "Retrieving results in MENDS ISO and its aliases"
      (d/assert-metadata-results-match
        :iso19115 all-colls
        (search/find-metadata :collection :iso19115 {}))
      (testing "as extension"
        (are [url-extension]
             (d/assert-metadata-results-match
               :iso19115 all-colls
               (search/find-metadata :collection :iso19115 {} {:url-extension url-extension}))
             "iso"
             "iso19115")))

    (testing "Retrieving results in SMAP ISO format is not supported"
      (is (= {:errors ["The mime types specified in the accept header [application/iso:smap+xml] are not supported."],
              :status 400}
             (search/get-search-failure-xml-data
               (search/find-metadata :collection :iso-smap {}))))
      (testing "as extension"
        (is (= {:errors ["The URL extension [iso_smap] is not supported."],
                :status 400}
               (search/get-search-failure-xml-data
                 (search/find-concepts-in-format
                   nil :collection {} {:url-extension "iso_smap"}))))))

    (testing "Retrieving results in dif10"
      (d/assert-metadata-results-match
        :dif10 all-colls
        (search/find-metadata :collection :dif10 {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :dif10 all-colls
          (search/find-metadata :collection :dif10 {} {:url-extension "dif10"}))))


    (testing "Retrieving results as XML References"
      (let [refs (search/find-refs :collection {:short-name "S1"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [c1-echo] refs))
        (testing "Location allows retrieval of native XML"
          (let [response (client/get location
                                     {:accept :application/echo10+xml
                                      :connection-manager (s/conn-mgr)})]
            (is (= (umm/umm->xml c1-echo :echo10) (:body response))))))

      (testing "as extension"
        (is (d/refs-match? [c1-echo] (search/find-refs :collection
                                                       {:short-name "S1"}
                                                       {:url-extension "xml"})))))
    (testing "ECHO Compatibility mode"
      (testing "XML References"
        (are [refs]
             (and (d/echo-compatible-refs-match? all-colls refs)
                  (= "array" (:type refs)))
             (search/find-refs :collection {:echo-compatible true})
             (search/find-refs-with-aql :collection [] [] {:query-params {:echo_compatible true}})))

      (testing "ECHO10"
        (d/assert-echo-compatible-metadata-results-match
          :echo10 all-colls
          (search/find-metadata :collection :echo10 {:echo-compatible true}))))))

; Tests that we can ingest and find difs with spatial and that granules in the dif can also be
; ingested and found
(deftest dif-with-spatial
  (let [c1 (d/ingest "PROV1" (dc/collection-dif {:spatial-coverage nil}) {:format :dif})
        g1 (d/ingest "PROV1" (dg/granule c1))

        ;; A collection with a granule spatial representation
        c2 (d/ingest "PROV1" (dc/collection-dif {:spatial-coverage (dc/spatial {:gsr :geodetic})})
                     {:format :dif})
        g2 (d/ingest "PROV1" (dg/granule c2 {:spatial-coverage (dg/spatial (m/mbr -160 45 -150 35))}))


        ;; A collections with a granule spatial representation and spatial data
        c3 (d/ingest "PROV1"
                     (dc/collection-dif
                       {:spatial-coverage (dc/spatial {:gsr :geodetic
                                                       :sr :geodetic
                                                       :geometries [(m/mbr -10 9 0 -10)]})})
                     {:format :dif})
        g3 (d/ingest "PROV1" (dg/granule c3 {:spatial-coverage (dg/spatial m/whole-world)}))]
    (index/wait-until-indexed)

    (testing "spatial search for dif collections"
      (are [wnes items]
           (let [found (search/find-refs :collection {:bounding-box (codec/url-encode (apply m/mbr wnes))})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (pr-str (map :entry-title items)))
               (println "Actual:" (->> found :refs (map :name) pr-str)))
             matches?)
           ;; whole world
           [-180 90 180 -90] [c3]
           [-180 90 -11 -90] []
           [-20 20 20 -20] [c3]))

    (testing "spatial search for granules in dif collections"
      (are [wnes items]
           (let [found (search/find-refs :granule {:bounding-box (codec/url-encode (apply m/mbr wnes))})
                 matches? (d/refs-match? items found)]
             (when-not matches?
               (println "Expected:" (pr-str (map :entry-title items)))
               (println "Actual:" (->> found :refs (map :name) pr-str)))
             matches?)
           ;; whole world
           [-180 90 180 -90] [g2 g3]
           [0 90 180 -90] [g3]
           [-180 90 0 -90] [g2 g3]))))

(deftest search-collection-various-formats
  (let [ru1 (dc/related-url {:type "GET DATA" :mime-type "application/json" :url "http://example.com"})
        ru2 (dc/related-url {:type "GET DATA" :mime-type "text/xml" :url "http://example2.com"})
        ru3 (dc/related-url {:type "GET RELATED VISUALIZATION" :mime-type "application/xml" :url "http://example.com/browse"})
        ru4 (dc/related-url {:type "VIEW PROJECT HOME PAGE" :url "http://example.com"})
        pr1 (dc/projects "project-short-name1" "project-short-name2" "project-short-name3")
        p1 (dc/personnel "John" "Smith" "jsmith@nasa.gov")
        p2 (dc/personnel "Jane" "Doe" nil)
        p3 (dc/personnel "Dummy" "Johnson" "johnson@nasa.gov")
        p4 (dc/personnel "John" "Dummy" "john@nasa.gov")
        op1 {:swath-width 1450.0
             :period 98.88
             :inclination-angle 98.15
             :number-of-orbits 0.5
             :start-circular-latitude -90.0}
        sk1 (dc/science-keyword {:category "Cat1"
                                 :topic "Topic1"
                                 :term "Term1"
                                 :variable-level-1 "Level1-1"
                                 :variable-level-2 "Level1-2"
                                 :variable-level-3 "Level1-3"
                                 :detailed-variable "Detail1"})
        sk2 (dc/science-keyword {:category "Hurricane"
                                 :topic "Popular"
                                 :term "Extreme"
                                 :variable-level-1 "Level2-1"
                                 :variable-level-2 "Level2-2"
                                 :variable-level-3 "Level2-3"
                                 :detailed-variable "UNIVERSAL"})

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes (poly/polygon [outer hole1 hole2])
        polygon-without-holes (poly/polygon [(umm-s/ords->ring -70 20, 70 20, 70 30, -70 30, -70 20)])

        coll1 (d/ingest "PROV1"
                        (dc/collection {:entry-title "Dataset1"
                                        :short-name "ShortName#1"
                                        :version-id "Version1"
                                        :summary "Summary of coll1"
                                        :organizations [(dc/org :archive-center "Larc")]
                                        :personnel [p1]
                                        :collection-data-type "NEAR_REAL_TIME"
                                        :processing-level-id "L1"
                                        :beginning-date-time "2010-01-01T12:00:00Z"
                                        :ending-date-time "2010-01-11T12:00:00Z"
                                        :related-urls [ru1 ru2]
                                        :associated-difs ["DIF-1" "DIF-2"]
                                        :science-keywords [sk1]
                                        :spatial-coverage
                                        (dc/spatial {:sr :geodetic
                                                     :gsr :geodetic
                                                     :geometries [polygon-without-holes
                                                                  polygon-with-holes
                                                                  (p/point 1 2)
                                                                  (p/point -179.9 89.4)
                                                                  (l/ords->line-string nil [0 0, 0 1, 0 -90, 180 0])
                                                                  (l/ords->line-string nil [1 2, 3 4, 5 6, 7 8])
                                                                  (m/mbr -180 90 180 -90)
                                                                  (m/mbr -10 20 30 -40)]})}))
        coll2 (d/ingest "PROV1"
                        (dc/collection {:entry-title "Dataset2"
                                        :short-name "ShortName#2"
                                        :version-id "Version2"
                                        :summary "Summary of coll2"
                                        :personnel [p2]
                                        :beginning-date-time "2010-01-01T12:00:00Z"
                                        :ending-date-time "2010-01-11T12:00:00Z"
                                        :related-urls [ru3]
                                        :science-keywords [sk1 sk2]
                                        :spatial-coverage
                                        (dc/spatial {:sr :cartesian
                                                     :gsr :cartesian
                                                     :geometries [polygon-without-holes]})}))
        coll3 (d/ingest "PROV1"
                        (dc/collection
                          {:entry-title "Dataset3"
                           :personnel [p3]
                           :spatial-coverage (dc/spatial {:gsr :orbit
                                                          :orbit op1})}))
        coll4 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset4"}) {:format :iso-smap})
        coll5 (d/ingest "PROV1" (dc/collection-dif {:entry-title "Dataset5"}) {:format :dif})
        coll6 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset6"
                                                :short-name "ShortName#6"
                                                :version-id "Version6"
                                                :summary "Summary of coll6"
                                                :organizations [(dc/org :archive-center "Larc")]
                                                :projects pr1
                                                :related-urls [ru4]
                                                :beginning-date-time "2010-01-01T12:00:00Z"
                                                :ending-date-time "2010-01-11T12:00:00Z"
                                                :spatial-coverage
                                                (dc/spatial {:sr :cartesian
                                                             :gsr :cartesian
                                                             :geometries [(p/point 1 2)
                                                                          (p/point -179.9 89.4)]})}))
        coll7 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset7"
                                                :short-name "ShortName#7"
                                                :version-id "Version7"
                                                :summary "Summary of coll7"
                                                :organizations [(dc/org :archive-center "Larc")]
                                                :personnel [p4]
                                                :beginning-date-time "2010-01-01T12:00:00Z"
                                                :ending-date-time "2010-01-11T12:00:00Z"
                                                :spatial-coverage
                                                (dc/spatial {:sr :cartesian
                                                             :gsr :cartesian
                                                             :geometries [(l/ords->line-string nil [0 0, 0 1, 0 -90, 180 0])
                                                                          (l/ords->line-string nil [1 2, 3 4, 5 6, 7 8])]})}))
        coll8 (d/ingest "USGS_EROS" (dc/collection {:entry-title "Dataset8"
                                                    :short-name "ShortName#8"
                                                    :version-id "Version8"
                                                    :summary "Summary of coll8"
                                                    :organizations [(dc/org :archive-center "Landsat")]
                                                    :beginning-date-time "2010-01-01T12:00:00Z"
                                                    :ending-date-time "2010-01-11T12:00:00Z"
                                                    :spatial-coverage
                                                    (dc/spatial {:sr :cartesian
                                                                 :gsr :cartesian
                                                                 :geometries [(m/mbr -180 90 180 -90)
                                                                              (m/mbr -10 20 30 -40)]})}))
        coll9 (d/ingest "PROV1"
                        (dc/collection-dif10 {:entry-title "Dataset9"})
                        {:format :dif10})]

    (index/wait-until-indexed)

    (testing "kml"
      (let [results (search/find-concepts-kml :collection {})]
        (dk/assert-collection-kml-results-match [coll1 coll2 coll3 coll4 coll5 coll6 coll7
                                                 coll8 coll9] results)))

    (testing "csv is not supported"
      (is (= {:errors ["The mime type [text/csv] is not supported for collections."],
              :status 400}
             (search/find-concepts-csv :collection {})))
      (testing "as csv extension"
        (is (= {:errors ["The mime type [text/csv] is not supported for collections."],
                :status 400}
               (search/find-concepts-csv :collection {} {:url-extension "csv"})))))

    (testing "opendata"
      (let [results (search/find-concepts-opendata :collection {})]
        (od/assert-collection-opendata-results-match [coll1 coll2 coll3 coll4 coll5 coll6 coll7
                                                      coll8 coll9] results))
      (testing "as extension"
        (let [results (search/find-concepts-opendata :collection {} {:url-extension "opendata"})]
          (od/assert-collection-opendata-results-match [coll1 coll2 coll3 coll4 coll5 coll6 coll7
                                                        coll8 coll9] results)))
      (testing "no opendata support for granules"
        (is (= {:errors ["The mime type [application/opendata+json] is not supported for granules."],
                :status 400}
               (search/find-concepts-opendata :granule {})))))

    (testing "ATOM XML"
      (let [coll-atom (da/collections->expected-atom [coll1] "collections.atom?dataset_id=Dataset1")
            response (search/find-concepts-atom :collection {:dataset-id "Dataset1"})
            {:keys [status results]} response]
        (is (= [200 coll-atom] [status results])))

      (let [coll-atom (da/collections->expected-atom [coll1 coll2 coll3 coll4 coll5 coll6 coll7
                                                      coll8 coll9] "collections.atom")
            response (search/find-concepts-atom :collection {})
            {:keys [status results]} response]
        (is (= [200 coll-atom] [status results])))

      (let [coll-atom (da/collections->expected-atom [coll3] "collections.atom?dataset_id=Dataset3")
            response (search/find-concepts-atom :collection {:dataset-id "Dataset3"})
            {:keys [status results]} response]
        (is (= [200 coll-atom] [status results])))

      (testing "as extension"
        (is (= (select-keys
                 (search/find-concepts-atom :collection {:dataset-id "Dataset1"})
                 [:status :results])
               (select-keys
                 (search/find-concepts-atom :collection
                                            {:dataset-id "Dataset1"}
                                            {:url-extension "atom"})
                 [:status :results])))))

    (testing "JSON"
      (let [coll-json (da/collections->expected-atom [coll1] "collections.json?dataset_id=Dataset1")
            response (search/find-concepts-json :collection {:dataset-id "Dataset1"})
            {:keys [status results]} response]
        (is (= [200 coll-json] [status results])))

      (let [coll-json (da/collections->expected-atom [coll1 coll2 coll3 coll4 coll5 coll6 coll7
                                                      coll8 coll9] "collections.json")
            response (search/find-concepts-json :collection {})
            {:keys [status results]} response]
        (is (= [200 coll-json] [status results])))

      (testing "as extension"
        (is (= (select-keys
                 (search/find-concepts-json :collection {:dataset-id "Dataset1"})
                 [:status :results])
               (select-keys
                 (search/find-concepts-json :collection
                                            {:dataset-id "Dataset1"}
                                            {:url-extension "json"})
                 [:status :results])))))))

(deftest formats-have-scores-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:long-name "ABC!XYZ" :entry-title "Foo"}))]
    (index/wait-until-indexed)
    (testing "XML references"
      (testing "XML has score for keyword search."
        (are [keyword-str scores]
             (= scores
                (map :score (:refs (search/find-refs :collection {:keyword keyword-str}))))
             "ABC" [0.7]
             "ABC Foo" [0.5]))
      (testing "XML has no score field for non-keyword search."
        (are [title-str scores]
             (= scores
                (map :score (:refs (search/find-refs :collection {:entry-title title-str}))))
             "Foo" [nil])))

    (testing "ATOM XML"
      (testing "Atom has score for keyword search."
        (are [keyword-str scores]
             (= scores
                (map :score (get-in (search/find-concepts-atom :collection
                                                               {:keyword keyword-str})
                                    [:results :entries])))
             "ABC" [0.7]
             "ABC Foo" [0.5]))
      (testing "Atom has no score field for non-keyword search."
        (are [title-str scores]
             (= scores
                (map :score (get-in (search/find-concepts-atom :collection {:entry-title title-str})
                                    [:results :entries])))
             "Foo" [nil])))
    (testing "ATOM JSON"
      (testing "JSON has score for keyword search."
        (are [keyword-str scores]
             (= scores
                (map :score (get-in (search/find-concepts-json :collection {:keyword keyword-str})
                                    [:results :entries])))
             "ABC" [0.7]
             "ABC Foo" [0.5]))
      (testing "JSON has no score field for non-keyword search."
        (are [title-str scores]
             (= scores
                (map :score (get-in (search/find-concepts-json :collection {:entry-title title-str})
                                    [:results :entries])))
             "Foo" [nil])))))

(deftest search-errors-in-json-or-xml-format
  (testing "invalid format"
    (is (= {:errors ["The mime types specified in the accept header [application/echo11+xml] are not supported."],
            :status 400}
           (search/get-search-failure-xml-data
             (search/find-concepts-in-format
               "application/echo11+xml" :collection {})))))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-refs :collection {:unsupported "dummy"})))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-concepts-json :collection {:unsupported "dummy"}))))
