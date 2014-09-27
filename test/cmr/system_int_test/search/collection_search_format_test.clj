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
            [cmr.system-int-test.utils.url-helper :as url]
            [cheshire.core :as json]
            [clj-http.client :as client]
            [cmr.umm.core :as umm]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.point :as p]
            [cmr.spatial.mbr :as m]
            [cmr.spatial.line-string :as l]
            [cmr.spatial.ring-relations :as rr]
            [cmr.spatial.codec :as codec]
            [cmr.umm.spatial :as umm-s]
            [clojure.data.xml :as x]
            [cmr.common.xml :as cx]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(comment

  (do
    (ingest/reset)
    (ingest/create-provider "provguid1" "PROV1")
    (ingest/create-provider "provguid2" "PROV2")  )

  )

;; Tests that we can ingest and find items in different formats
(deftest multi-format-search-test
  (let [c1-echo (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                                  :version-id "V1"
                                                  :entry-title "ET1"})
                          :echo10)
        c2-echo (d/ingest "PROV2" (dc/collection {:short-name "S2"
                                                  :version-id "V2"
                                                  :entry-title "ET2"})
                          :echo10)
        c3-dif (d/ingest "PROV1" (dc/collection {:entry-id "S3"
                                                 :short-name "S3"
                                                 :version-id "V3"
                                                 :entry-title "ET3"
                                                 :long-name "ET3"})
                         :dif)
        c4-dif (d/ingest "PROV2" (dc/collection {:entry-id "S4"
                                                 :short-name "S4"
                                                 :version-id "V4"
                                                 :entry-title "ET4"
                                                 :long-name "ET4"})
                         :dif)
        c5-iso (d/ingest "PROV1" (dc/collection {:short-name "S5"
                                                 :version-id "V5"})
                         :iso)
        c6-iso (d/ingest "PROV2" (dc/collection {:short-name "S6"
                                                 :version-id "V6"})
                         :iso-mends)
        c7-smap (d/ingest "PROV1" (dc/collection {:short-name "S7"
                                                  :version-id "V7"})
                          :iso-smap)
        all-colls [c1-echo c2-echo c3-dif c4-dif c5-iso c6-iso c7-smap]]
    (index/refresh-elastic-index)

    (testing "Finding refs ingested in different formats"
      (are [search expected]
           (d/refs-match? expected (search/find-refs :collection search))
           {} all-colls
           {:short-name "S4"} [c4-dif]
           {:entry-title "ET3"} [c3-dif]
           {:version ["V3" "V2"]} [c2-echo c3-dif]
           {:short-name "S5"} [c5-iso]
           {:short-name "S6"} [c6-iso]
           {:version "V5"} [c5-iso]
           {:version ["V5" "V6"]} [c5-iso c6-iso]
           {:short-name "S7"} [c7-smap]
           {:version "V7"} [c7-smap]))

    (testing "Retrieving results in echo10"
      (d/assert-metadata-results-match
        :echo10 all-colls
        (search/find-metadata :collection :echo10 {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :echo10 all-colls
          (search/find-metadata :collection :echo10 {} {:format-as-ext? true}))))

    (testing "Retrieving results in dif"
      (d/assert-metadata-results-match
        :dif all-colls
        (search/find-metadata :collection :dif {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :dif all-colls
          (search/find-metadata :collection :dif {} {:format-as-ext? true}))))

    (testing "Retrieving results in MENDS ISO and its aliases"
      (d/assert-metadata-results-match
        :iso19115 all-colls
        (search/find-metadata :collection :iso19115 {}))
      (testing "as extension"
        (are [format-key]
             (d/assert-metadata-results-match
               format-key all-colls
               (search/find-metadata :collection format-key {} {:format-as-ext? true}))
             :iso
             :iso19115)))

    (testing "Retrieving results in SMAP ISO"
      (d/assert-metadata-results-match
        :iso-smap all-colls
        (search/find-metadata :collection :iso-smap {}))
      (testing "as extension"
        (d/assert-metadata-results-match
          :iso-smap all-colls
          (search/find-metadata :collection :iso-smap {} {:format-as-ext? true}))))

    (testing "Get by concept id in formats"
      (testing "supported formats"
        (are [mime-type format-key format-as-ext?]
             (let [response (search/get-concept-by-concept-id
                              (:concept-id c1-echo)
                              {:format-as-ext? format-as-ext? :accept mime-type})]
               (= (umm/umm->xml c1-echo format-key) (:body response)))
             "application/dif+xml" :dif false
             "application/dif+xml" :dif true
             "application/echo10+xml" :echo10 false
             "application/echo10+xml" :echo10 true))
      (testing "native format"
        ;; Native format can be specified using application/xml or not specifying any format
        (let [response (search/get-concept-by-concept-id (:concept-id c1-echo) {:accept nil})]
          (is (= (umm/umm->xml c1-echo :echo10) (:body response))))

        (let [response (search/get-concept-by-concept-id (:concept-id c3-dif) {:accept nil})]
          (is (= (umm/umm->xml c3-dif :dif) (:body response)))))
      (testing "unsupported formats"
        (are [mime-type xml?]
             (let [response (search/get-concept-by-concept-id
                              (:concept-id c1-echo)
                              {:format-as-ext? true :accept mime-type})
                   err-msg (if xml?
                             (cx/string-at-path (x/parse-str (:body response)) [:error])
                             (first (:errors (json/decode (:body response) true))))]
               (and (= 400 (:status response))
                    (= (str "The mime type [" mime-type "] is not supported.") err-msg)))
             "application/atom+xml" true
             "application/json" false
             "text/csv" false)))

    (testing "Retrieving results as XML References"
      (let [refs (search/find-refs :collection {:short-name "S1"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [c1-echo] refs))
        (testing "Location allows retrieval of native XML"
          (let [response (client/get location
                                     {:accept :application/echo10+xml
                                      :connection-manager (url/conn-mgr)})]
            (is (= (umm/umm->xml c1-echo :echo10) (:body response))))))

      (testing "as extension"
        (is (d/refs-match? [c1-echo] (search/find-refs :collection
                                                       {:short-name "S1"}
                                                       {:format-as-ext? true})))))
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
  (let [c1 (d/ingest "PROV1" (dc/collection {:spatial-coverage nil}) :dif)
        g1 (d/ingest "PROV1" (dg/granule c1))

        ;; A collection with a granule spatial representation
        c2 (d/ingest "PROV1" (dc/collection {:spatial-coverage (dc/spatial {:gsr :geodetic})}) :dif)
        g2 (d/ingest "PROV1" (dg/granule c2 {:spatial-coverage (dg/spatial (m/mbr -160 45 -150 35))}))


        ;; A collections with a granule spatial representation and spatial data
        c3 (d/ingest "PROV1"
                     (dc/collection
                       {:spatial-coverage
                        (dc/spatial {:gsr :geodetic
                                     :sr :geodetic
                                     :geometries [(m/mbr -10 9 0 -10)]})})
                     :dif)
        g3 (d/ingest "PROV1" (dg/granule c3))]
    (index/refresh-elastic-index)

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
           [-180 90 180 -90] [g2]
           [0 90 180 -90] []
           [-180 90 0 -90] [g2]))))

(deftest search-collection-atom-and-json
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")

        ;; polygon with holes
        outer (umm-s/ords->ring -5.26,-2.59, 11.56,-2.77, 10.47,8.71, -5.86,8.63, -5.26,-2.59)
        hole1 (umm-s/ords->ring 6.95,2.05, 2.98,2.06, 3.92,-0.08, 6.95,2.05)
        hole2 (umm-s/ords->ring 5.18,6.92, -1.79,7.01, -2.65,5, 4.29,5.05, 5.18,6.92)
        polygon-with-holes (poly/polygon [outer hole1 hole2])

        coll1 (d/ingest "PROV1"
                        (dc/collection {:entry-title "Dataset1"
                                        :short-name "ShortName#1"
                                        :version-id "Version1"
                                        :summary "Summary of coll1"
                                        :organizations [(dc/org :archive-center "Larc")]
                                        :collection-data-type "NEAR_REAL_TIME"
                                        :processing-level-id "L1"
                                        :beginning-date-time "2010-01-01T12:00:00Z"
                                        :ending-date-time "2010-01-11T12:00:00Z"
                                        :related-urls [ru1 ru2]
                                        :associated-difs ["DIF-1" "DIF-2"]
                                        :spatial-coverage
                                        (dc/spatial {:sr :geodetic
                                                     :gsr :geodetic
                                                     :shapes [(poly/polygon [(umm-s/ords->ring -70 20, 70 20, 70 30, -70 30, -70 20)])
                                                              polygon-with-holes
                                                              (p/point 1 2)
                                                              (p/point -179.9 89.4)
                                                              (l/ords->line-string nil 0 0, 0 1, 0 -90, 180 0)
                                                              (l/ords->line-string nil 1 2, 3 4, 5 6, 7 8)
                                                              (m/mbr -180 90 180 -90)
                                                              (m/mbr -10 20 30 -40)]})}))
        coll2 (d/ingest "PROV1"
                        (dc/collection {:entry-title "Dataset2"
                                        :short-name "ShortName#2"
                                        :version-id "Version2"
                                        :summary "Summary of coll2"
                                        :beginning-date-time "2010-01-01T12:00:00Z"
                                        :ending-date-time "2010-01-11T12:00:00Z"
                                        :related-urls [ru3]
                                        :spatial-coverage
                                        (dc/spatial {:sr :cartesian
                                                     :gsr :cartesian
                                                     :geometries [(poly/polygon [(umm-s/ords->ring -70 20, 70 20, 70 30, -70 30, -70 20)])]})}))]

    (index/refresh-elastic-index)

    (let [coll-atom (da/collections->expected-atom [coll1] "collections.atom?dataset_id=Dataset1")
          response (search/find-concepts-atom :collection {:dataset-id "Dataset1"})]
      (is (= 200 (:status response)))
      (is (= coll-atom
             (:results response))))

    (let [coll-atom (da/collections->expected-atom [coll1 coll2] "collections.atom")
          response (search/find-concepts-atom :collection {})]
      (is (= 200 (:status response)))
      (is (= coll-atom
             (:results response))))

    (testing "as extension"
      (is (= (select-keys
               (search/find-concepts-atom :collection {:dataset-id "Dataset1"})
               [:status :results])
             (select-keys
               (search/find-concepts-atom :collection
                                          {:dataset-id "Dataset1"}
                                          {:format-as-ext? true})
               [:status :results]))))

    ;; search json format
    (let [coll-json (da/collections->expected-atom [coll1] "collections.json?dataset_id=Dataset1")
          response (search/find-concepts-json :collection {:dataset-id "Dataset1"})]
      (is (= 200 (:status response)))
      (is (= coll-json
             (:results response))))

    (let [coll-json (da/collections->expected-atom [coll1 coll2] "collections.json")
          response (search/find-concepts-json :collection {})]
      (is (= 200 (:status response)))
      (is (= coll-json
             (:results response))))

    (testing "as extension"
      (is (= (select-keys
               (search/find-concepts-json :collection {:dataset-id "Dataset1"})
               [:status :results])
             (select-keys
               (search/find-concepts-json :collection
                                          {:dataset-id "Dataset1"}
                                          {:format-as-ext? true})
               [:status :results]))))))

(deftest atom-has-score-for-keyword-search
  (let [coll1 (d/ingest "PROV1" (dc/collection {:long-name "ABC!XYZ" :entry-title "Foo"}))]
    (index/refresh-elastic-index)
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
           "Foo" [nil]))))

(deftest reference-xml-has-score-for-keyword-search
  (let [coll1 (d/ingest "PROV1" (dc/collection {:long-name "ABC!XYZ" :entry-title "Foo"}))]
    (index/refresh-elastic-index)
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
           "Foo" [nil]))))

(deftest json-atom-has-score-for-keyword-search
  (let [coll1 (d/ingest "PROV1" (dc/collection {:long-name "ABC!XYZ" :entry-title "Foo"}))]
    (index/refresh-elastic-index)
    (testing "XML has score for keyword search."
      (are [keyword-str scores]
           (= scores
              (map :score (get-in (search/find-refs-json :collection {:keyword keyword-str})
                                  [:feed :entry])))
           "ABC" [0.7]
           "ABC Foo" [0.5]))
    (testing "XML has no score field for non-keyword search."
      (are [title-str scores]
           (= scores
              (map :score (get-in (search/find-refs-json :collection {:entry-title title-str})
                                  [:feed :entry])))
           "Foo" [nil]))))

(deftest search-errors-in-json-or-xml-format
  (testing "invalid format"
    (is (= {:errors ["The mime type [application/echo11+xml] is not supported."],
            :status 400}
           (search/get-search-failure-xml-data
             (search/find-concepts-in-format
               "application/echo11+xml" :collection {})))))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-refs :collection {:unsupported "dummy"})))

  (is (= {:status 400,
          :errors ["Parameter [unsupported] was not recognized."]}
         (search/find-refs-json :collection {:unsupported "dummy"}))))
