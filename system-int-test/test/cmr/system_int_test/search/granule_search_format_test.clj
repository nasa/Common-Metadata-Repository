(ns cmr.system-int-test.search.granule-search-format-test
  "Integration tests for searching granules in various formats"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.concepts :as cu]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.spatial.ring-relations :as rr]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.atom-json :as dj]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.kml :as dk]
   [cmr.system-int-test.data2.stac :as stac]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.umm.umm-core :as umm]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.umm.umm-spatial :as umm-s]
   [ring.util.codec :as codec]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(comment

  (do
    (dev-sys-util/reset)
    (ingest/create-provider {:provider-guid "provguid1" :provider-id "PROV1"})
    (ingest/create-provider {:provider-guid "provguid2" :provider-id "PROV2"})))

(deftest simple-search-test-for-gran-echo-1.6.3-schema
  (let [coll-7340 (d/ingest-concept-with-metadata-file "CMR-7340/Coll-CMR-7340.echo10"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "echo10-collection"
                                                   :format-key :echo10})
        ;; This granule contains additional file added in the new schema
        gran-7340 (d/ingest-concept-with-metadata-file "CMR-7340/Gran-CMR-7340.echo10"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :native-id "echo10-granule"
                                                      :format-key :echo10})]
    (index/wait-until-indexed)
    (let [params {:concept-id (:concept-id gran-7340)}
          options {:accept nil
                   :url-extension "native"}
          format-key :echo10
          response (search/find-metadata :granule format-key params options)]
      (is (= 1 (count (:items response))))
      (is (= (:concept-id (first (:items response))) (:concept-id gran-7340))))))

(deftest simple-search-test
  (let [c1-echo (d/ingest "PROV1" (dc/collection) {:format :echo10})
        g1-echo (d/ingest "PROV1" (dg/granule c1-echo {:granule-ur "g1"
                                                       :producer-gran-id "p1"})
                          {:format :echo10})]
    (index/wait-until-indexed)
    (let [params {:concept-id (:concept-id g1-echo)}
          options {:accept nil
                   :url-extension "native"}
          format-key :echo10
          response (search/find-metadata :granule format-key params options)]
      (d/assert-metadata-results-match format-key [g1-echo] response))))

(deftest search-smap-granule-with-size-in-echo10
  (let [coll (d/ingest-concept-with-metadata-file "CMR-4902/4902_smap_iso_collection.xml"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "iso-smap-collection"
                                                   :format-key :iso-smap})
        ;; The iso smap granule contains transferSize being 22.2031965255737
        granule (d/ingest-concept-with-metadata-file "CMR-4902/4902_smap_iso_granule.xml"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :native-id "iso-smap-granule"
                                                      :format-key :iso-smap})
        umm-granule-size (get-in granule [:data-granule :size])]
    (index/wait-until-indexed)
    (let [params {:concept-id (:concept-id granule)}
          format-key :echo10
          response (search/find-metadata :granule format-key params)
          metadata (:metadata (first (:items response)))]
      ;; Umm granule size should be the same as the transferSize in the smap granule.
      ;; The retrieved echo10 granule should contain the same SizeMBDataGranule as the umm granule size.
      (is (= 22.2031965255737 umm-granule-size))
      (is (= true (.contains metadata (str "<SizeMBDataGranule>" umm-granule-size "</SizeMBDataGranule>")))))))

(deftest search-umm-g-granule-test
  (let [collection (d/ingest-umm-spec-collection
                    "PROV1"
                    (data-umm-c/collection {:EntryTitle "UMM-G-Test"
                                            :ShortName "U-G-T"
                                            :Version "V1"}))
        granule (dg/granule-with-umm-spec-collection
                 collection
                 (:concept-id collection)
                 {:granule-ur "g1"
                  :collection-ref (umm-g/map->CollectionRef {:entry-title "UMM-G-Test"})})
        echo10-gran (d/item->concept granule :echo10)
        iso19115-gran (d/item->concept granule :iso19115)
        umm-g-gran (d/item->concept granule :umm-json)
        umm-g-gran-concept-id (:concept-id (ingest/ingest-concept umm-g-gran))]
    (index/wait-until-indexed)
    (testing "Search UMM-G various formats"
      (util/are3 [format-key granule-concept-id expected-granule accept url-extension]
        (let [response (search/find-concepts-in-format
                        format-key
                        :granule
                        {:concept-id granule-concept-id}
                        {:accept accept
                         :url-extension url-extension})
              response-format (mt/mime-type->format (get-in response [:headers :Content-Type]))]
          (is (= 200 (:status response)))
          (is (= format-key response-format))
          (if (= :iso19115 format-key) ; iso19115 item->concept doesn't exist, just do a ISO granule element test for now
            (is (re-matches #"(?s).*<gmi:MI_Metadata.*" (:body response)))
            (is (string/includes? (:body response) (:metadata expected-granule)))))

        "Search echo10 via accept"
        :echo10 umm-g-gran-concept-id echo10-gran "application/echo10+xml" nil

        "Search echo10 via extension"
        :echo10 umm-g-gran-concept-id echo10-gran nil "echo10"

        "Search iso19115 via accept"
        :iso19115 umm-g-gran-concept-id iso19115-gran "application/iso19115+xml" nil

        "Search iso19115 via extension"
        :iso19115 umm-g-gran-concept-id iso19115-gran nil "iso19115"

        "Search native via accept"
        :native umm-g-gran-concept-id umm-g-gran "application/metadata+xml" nil

        "Search native via extension"
        :native umm-g-gran-concept-id umm-g-gran nil "native"))))

(deftest search-granules-in-xml-metadata
  (let [c1-echo (d/ingest "PROV1" (dc/collection) {:format :echo10})
        c2-smap (d/ingest "PROV2" (dc/collection) {:format :iso-smap})
        g1-echo (d/ingest "PROV1" (dg/granule c1-echo {:granule-ur "g1"
                                                       :producer-gran-id "p1"}) {:format :echo10})
        g2-echo (d/ingest "PROV1" (dg/granule c1-echo {:granule-ur "g2"
                                                       :producer-gran-id "p2"}) {:format :echo10})
        g1-smap (d/ingest "PROV2" (dg/granule c2-smap {:granule-ur "g3"
                                                       :producer-gran-id "p3"}) {:format :iso-smap})
        g2-smap (d/ingest "PROV2" (dg/granule c2-smap {:granule-ur "g4"
                                                       :producer-gran-id "p2"}) {:format :iso-smap})
        ;; An item ingested with and XML preprocessing line to ensure this is tested
        item (assoc (dg/granule c1-echo {:granule-ur "g5"
                                         :producer-gran-id "p5"})
                    :provider-id "PROV1")
        concept (-> (d/item->concept item :echo10)
                    (update :metadata #(str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" %)))
        response (ingest/ingest-concept concept)
        _ (is (= 201 (:status response)))
        g5-echo (assoc item
                       :concept-id (:concept-id response)
                       :revision-id (:revision-id response)
                       :format-key :echo10)
        all-granules [g1-echo g2-echo g1-smap g2-smap g5-echo]]
    (index/wait-until-indexed)

    (testing "Finding refs ingested in different formats"
      (are [search expected]
        (d/refs-match? expected (search/find-refs :granule search))
        {} all-granules
        {:granule-ur "g1"} [g1-echo]
        {:granule-ur "g5"} [g5-echo]
        {:granule-ur "g3"} [g1-smap]
        {:producer-granule-id "p1"} [g1-echo]
        {:producer-granule-id "p3"} [g1-smap]
        {:producer-granule-id "p2"} [g2-echo g2-smap]
        {:granule-ur ["g1" "g4"]} [g1-echo g2-smap]
        {:producer-granule-id ["p1" "p3"]} [g1-echo g1-smap]))

    (testing "Retrieving results in native format"
      ;; Native format for search can be specified using Accept header application/metadata+xml
      ;; or the .native extension.
      (util/are2 [concepts format-key extension accept]
        (let [params {:concept-id (map :concept-id concepts)}
              options (-> {:accept nil}
                          (merge (when extension {:url-extension extension}))
                          (merge (when accept {:accept accept})))
              response (search/find-metadata :granule format-key params options)]
          (d/assert-metadata-results-match format-key concepts response))
        "ECHO10 .native extension" [g1-echo g2-echo g5-echo] :echo10 "native" nil
        "SMAP ISO .native extension" [g1-smap g2-smap] :iso-smap "native" nil
        "ECHO10 accept application/metadata+xml" [g1-echo g2-echo g5-echo] :echo10 nil "application/metadata+xml"
        "SMAP ISO accept application/metadata+xml" [g1-smap g2-smap] :iso-smap nil "application/metadata+xml"))

    (testing "native format direct retrieval"
      ;; Native format can be specified using application/xml, application/metadata+xml,
      ;; .native extension, or not specifying any format.
      (util/are3 [concept format-key extension accept]
        (let [options (-> {:accept nil}
                          (merge (when extension {:url-extension extension}))
                          (merge (when accept {:accept accept})))
              response (search/retrieve-concept (:concept-id concept) nil options)]
          (is (search/mime-type-matches-response? response (mt/format->mime-type format-key)))
          (is (= (umm/umm->xml concept format-key) (:body response))))
        "ECHO10 no extension" g1-echo :echo10 nil nil
        "SMAP ISO no extension" g1-smap :iso-smap nil nil
        "ECHO10 .native extension" g1-echo :echo10 "native" nil
        "SMAP ISO .native extension" g1-smap :iso-smap "native" nil
        "ECHO10 accept application/xml" g1-echo :echo10 nil "application/xml"
        "SMAP ISO accept application/xml" g1-smap :iso-smap nil "application/xml"
        "ECHO10 accept application/metadata+xml" g1-echo :echo10 nil "application/metadata+xml"
        "SMAP ISO accept application/metadata+xml" g1-smap :iso-smap nil "application/metadata+xml"))

    (testing "Get granule as concept in JSON format"
      (are [granule coll options]
        (let [resp (search/retrieve-concept (:concept-id granule) nil options)]
          (and
           (search/mime-type-matches-response? resp mt/json)
           (= (da/granule->expected-atom granule coll)
              (dissoc
               (dj/parse-json-granule (:body resp))
               :day-night-flag))))
        g1-echo c1-echo {:accept        "application/json"}
        g1-echo c1-echo {:url-extension "json"}
        g1-smap c2-smap {:accept        "application/json"}
        g1-smap c2-smap {:url-extension "json"}))

    (testing "Get granule as concept in Atom format"
      (are [granule coll options]
        (let [resp (search/retrieve-concept (:concept-id granule) nil options)]
          (and
           (search/mime-type-matches-response? resp mt/atom)
           (= [(da/granule->expected-atom granule coll)]
              (map #(dissoc % :day-night-flag)
                   (:entries
                    (da/parse-atom-result :granule (:body resp)))))))
        g1-echo c1-echo {:accept        "application/atom+xml"}
        g1-echo c1-echo {:url-extension "atom"}
        g1-smap c2-smap {:accept        "application/atom+xml"}
        g1-smap c2-smap {:url-extension "atom"}))

    (testing "Retrieving results in echo10"
      (are [search expected]
        (d/assert-metadata-results-match
         :echo10 expected
         (search/find-metadata :granule :echo10 search))
        {} all-granules
        {:granule-ur "g1"} [g1-echo]
        {:granule-ur "g5"} [g5-echo]
        {:granule-ur "g3"} [g1-smap]
        {:concept-id (map :concept-id [g1-echo g5-echo g1-smap])} [g1-echo g5-echo g1-smap])

      (testing "as extension"
        (d/assert-metadata-results-match
         :echo10 [g1-echo g5-echo]
         (search/find-metadata :granule :echo10
                               {:granule-ur ["g1" "g5"]}
                               {:url-extension "echo10"}))))

    (testing "Retrieving results in SMAP ISO format is not supported"
      (is (= {:errors ["The mime types specified in the accept header [application/iso:smap+xml] are not supported."],
              :status 400}
             (search/get-search-failure-xml-data
              (search/find-metadata :granule :iso-smap {}))))
      (testing "as extension"
        (is (= {:errors ["The URL extension [iso_smap] is not supported."],
                :status 400}
               (search/get-search-failure-xml-data
                (search/find-concepts-in-format
                 nil :granule {} {:url-extension "iso_smap"}))))))

    (testing "Retrieving granule results in DIF format is not supported"
      (is (= {:errors ["The mime type [application/dif+xml] is not supported for granules."],
              :status 400}
             (search/get-search-failure-xml-data
              (search/find-metadata :granule :dif {}))))
      (testing "as extension"
        (is (= {:errors ["The mime type [application/dif+xml] is not supported for granules."],
                :status 400}
               (search/get-search-failure-xml-data
                (search/find-concepts-in-format
                 nil :granule {} {:url-extension "dif"}))))))

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
      (is (= {:errors ["The mime types specified in the accept header [application/echo11+xml] are not supported."],
              :status 400}
             (search/get-search-failure-xml-data
              (search/find-concepts-in-format
               "application/echo11+xml" :granule {})))))

    (testing "invalid extension"
      (is (= {:errors ["The URL extension [echo11] is not supported."],
              :status 400}
             (search/get-search-failure-xml-data
              (client/get (str (url/search-url :granule) ".echo11")
                          {:connection-manager (s/conn-mgr)})))))

    (testing "invalid param defaults to XML error response"
      (is (= {:errors ["Parameter [foo] was not recognized."],
              :status 400}
             (search/get-search-failure-xml-data
              (client/get (str (url/search-url :granule) "?foo=bar")
                          {:connection-manager (s/conn-mgr)})))))

    (testing "invalid param with JSON accept header returns JSON error response"
      (is (= {:errors ["Parameter [foo] was not recognized."],
              :status 400}
             (search/get-search-failure-data
              (client/get (str (url/search-url :granule) "?foo=bar")
                          {:accept :application/json
                           :connection-manager (s/conn-mgr)})))))

    (testing "format extension takes precedence over accept header"
      (is (= {:errors ["Parameter [foo] was not recognized."],
              :status 400}
             (search/get-search-failure-data
              (client/get (str (url/search-url :granule) ".json?foo=bar")
                          {:accept :application/xml
                           :connection-manager (s/conn-mgr)})))))

    (testing "reference XML"
      (let [refs (search/find-refs :granule {:granule-ur "g1"})
            location (:location (first (:refs refs)))]
        (is (d/refs-match? [g1-echo] refs))

        (testing "Location allows granule native format retrieval"
          (let [response (client/get location
                                     {:accept :application/echo10+xml
                                      :connection-manager (s/conn-mgr)})]
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
  (let [ru1 (dc/related-url {:type "GET DATA" :url "http://example.com"})
        ru2 (dc/related-url {:type "GET DATA" :url "http://example2.com"})
        ru3 (dc/related-url {:type "GET RELATED VISUALIZATION" :url "http://example.com/browse"})
        coll1 (d/ingest "PROV1" (dc/collection {:beginning-date-time "1970-01-01T12:00:00Z"}))
        coll2 (d/ingest "PROV1" (dc/collection {:beginning-date-time "1970-01-01T12:00:00Z"}))
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

    (index/wait-until-indexed)

    (let [response (search/find-concepts-csv :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00.000Z,2010-01-11T12:00:00.000Z,\"http://example.com,http://example2.com\",http://example.com/browse,50.0,DAY,100.0\n")
             (:body response))))
    (let [response (search/find-concepts-csv :granule {:granule-ur "Granule2"})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule2,Granule #2,2011-01-01T12:00:00.000Z,2011-01-11T12:00:00.000Z,http://example.com,,30.0,NIGHT,80.0\n")
             (:body response))))
    (let [response (search/find-concepts-csv :granule {})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00.000Z,2010-01-11T12:00:00.000Z,\"http://example.com,http://example2.com\",http://example.com/browse,50.0,DAY,100.0\n"
                  "Granule2,Granule #2,2011-01-01T12:00:00.000Z,2011-01-11T12:00:00.000Z,http://example.com,,30.0,NIGHT,80.0\n"
                  "Granule3,Granule #3,2012-01-01T12:00:00.000Z,2012-01-11T12:00:00.000Z,,,30.0,NIGHT,80.0\n")
             (:body response))))

    (testing "as extension"
      (is (= (select-keys (search/find-concepts-csv :granule {:granule-ur "Granule1"})
                          [:status :body])
             (select-keys (search/find-concepts-csv :granule
                                                    {:granule-ur "Granule1"}
                                                    {:url-extension "csv"})
                          [:status :body]))))))

(deftest search-granule-atom-and-json-and-kml
  (let [ru1 (dc/related-url {:type "GET DATA" :url "http://example.com"})
        ru2 (dc/related-url {:type "GET DATA" :url "http://example2.com"})
        ru3 (dc/related-url {:type "GET RELATED VISUALIZATION" :url "http://example.com/browse"})
        ru4 (dc/related-url {:type "ALGORITHM INFO" :url "http://inherited.com"})
        ru5 (dc/related-url {:type "GET RELATED VISUALIZATION" :url "http://inherited.com/browse"})
        ru6 (dc/related-url {:type "USE SERVICE API"
                             :sub-type "OPENDAP DATA"
                             :url "https://ghrc.nsstc.nasa.gov/opendap/ssmis/f16/daily/data/"})
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "1970-01-01T12:00:00Z"
                                                :spatial-coverage (dc/spatial {:gsr :geodetic})}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset2"
                                                :beginning-date-time "1970-01-01T12:00:00Z"
                                                :related-urls [ru4 ru5]}))
        coll3 (d/ingest "PROV1" (dc/collection {:entry-title "OrbitDataset"
                                                :beginning-date-time "1970-01-01T12:00:00Z"
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
                                :size (Double/MAX_VALUE)
                                :cloud-cover 50.0
                                :orbit-calculated-spatial-domains [{:orbital-model-name "MODEL NAME"
                                                                    :orbit-number 2
                                                                    :start-orbit-number 3
                                                                    :stop-orbit-number 4
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
                                                    [(rr/ords->ring :geodetic [-70 20, 70 20, 70 30, -70 30, -70 20])])
                                                   polygon-with-holes
                                                   (p/point 1 2)
                                                   (p/point -179.9 89.4)
                                                   (l/ords->line-string :geodetic [0 0, 0 1, 0 -90, 180 0])
                                                   (l/ords->line-string :geodetic [1 2, 3 4, 5 6, 7 8])
                                                   (m/mbr -180 90 180 -90)
                                                   (m/mbr -10 20 30 -40))})
        gran2 (make-gran coll2 {:granule-ur "Granule2"
                                :beginning-date-time "2011-01-01T12:00:00Z"
                                :ending-date-time "2011-01-11T12:00:00Z"
                                :producer-gran-id "Granule #2"
                                :day-night "NIGHT"
                                :size 80.0
                                :cloud-cover 30.0
                                :related-urls [ru6]})
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
                                  :start-orbit-number 3
                                  :stop-orbit-number 4
                                  :equator-crossing-longitude -45.0
                                  :equator-crossing-date-time "2011-01-01T12:00:00.000Z"}]})
        gran4 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule4"
                                                   :spatial-coverage (dg/spatial (p/point 1 2))})
                        {:format :iso-smap})
        ;; Granule #5 is added for CMR-1115, where a granule with orbit spatial but no
        ;; OrbitCalculatedSpatialDomains will not have polygon info in its atom/json representation.
        gran5 (make-gran coll3 {:granule-ur "OrbitGranuleWithoutOrbitCalculatedSpatialDomains"
                                :producer-gran-id "Granule #5"
                                :day-night "NIGHT"
                                :size 80.0
                                :cloud-cover 30.0
                                :related-urls [ru3]
                                :spatial-coverage (dg/spatial (dg/orbit 120.0 50.0 :asc 50.0 :asc))})]

    (index/wait-until-indexed)

    (testing "kml"
      (let [results (search/find-concepts-kml :granule {})]
        (dk/assert-granule-kml-results-match
         [gran1 gran2 gran3 gran4 gran5] [coll1 coll2 coll3 coll1 coll3] results)))

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
                       [gran1 gran2 gran3 gran4 gran5] [coll1 coll2 coll3 coll1 coll3] "granules.atom")
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
                [:status :results]))))

      (testing "granule size is double"
        (let [response (search/find-concepts-atom :granule {:concept-id (:concept-id gran1)})]
          (is (= (Double/MAX_VALUE)
                 (-> response :results :entries first :size))))))

    (testing "json"
      (let [gran-json (dj/granules->expected-json [gran1] [coll1] "granules.json?granule_ur=Granule1")
            response (search/find-concepts-json :granule {:granule-ur "Granule1"})]
        (is (= 200 (:status response)))
        (is (= gran-json
               (:results response))))

      (let [gran-json (dj/granules->expected-json
                       [gran1 gran2 gran3 gran4 gran5] [coll1 coll2 coll3 coll1 coll3] "granules.json")
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

(deftest search-granules-related-url-translation-and-atom-test
  "Test the RelatedURLs of GET DATA VIA DIRECT ACCESS translations between ECHO10, ISO SMAP, UMM-G,
   ISO 19115 MENDS and old cmr umm-g. Also test the atom link."
  (let [ru1 (dc/related-url {:type "GET DATA" :url "http://example.com"})
        ru2 (dc/related-url {:type "GET DATA VIA DIRECT ACCESS" :url "s3://aws.com"})
        ru3 (dc/related-url {:type "GET RELATED VISUALIZATION" :url "http://example.com/browse"})
        coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "1970-01-01T12:00:00Z"
                                                :spatial-coverage (dc/spatial {:gsr :no-spatial})}))
        make-gran (fn [coll attribs]
                    (d/ingest "PROV1" (dg/granule coll attribs)))

        gran-echo (make-gran coll1 {:granule-ur "Granule1"
                                    :beginning-date-time "2010-01-01T12:00:00Z"
                                    :ending-date-time "2010-01-11T12:00:00Z"
                                    :producer-gran-id "Granule #1"
                                    :day-night "DAY"
                                    :size 100
                                    :cloud-cover 50
                                    :related-urls [ru1 ru2 ru3]})
        gran-smap (d/ingest "PROV1"
                            (dg/granule coll1 {:granule-ur "Granule2"
                                               :related-urls [ru1 ru2 ru3]})
                            {:format :iso-smap})
        gran-umm  (d/ingest "PROV1"
                            (dg/granule coll1 {:granule-ur "Granule3"
                                               :related-urls [ru1 ru2 ru3]})
                            {:format :umm-json})
        gran-no-ru (d/ingest "PROV1"
                             (dg/granule coll1 {:granule-ur "Granule4"})
                             {:format :umm-json})]

    (index/wait-until-indexed)

    (util/are3 [granule granule-ur]
      (let [gran-atom (da/granules->expected-atom [granule]
                                                  [coll1]
                                                  (str "granules.atom?granule_ur=" granule-ur))
            gran-atom-links (-> gran-atom
                                (get :entries)
                                (first)
                                (get :links))
            response (search/find-concepts-atom :granule {:granule-ur granule-ur})
            response-links (-> response
                               (get-in [:results :entries])
                               (first)
                               (get :links))]
        (is (= 200 (:status response)))
        (is (= (:rel gran-atom-links) (:rel response-links)))
        (is (= (:href gran-atom-links) (:href response-links)))
        (is (some #(string/includes? % "s3#") response-links)))

      "testing echo10"
      gran-echo
      "Granule1"

      "testing iso smap"
      gran-smap
      "Granule2"

      "testing umm-g"
      gran-umm
      "Granule3")

    (testing "testing without related URLs."
      (let [gran-atom (da/granules->expected-atom [gran-no-ru] [coll1] "granules.atom?granule_ur=Granule4")
            gran-atom-links (-> gran-atom
                                (get :entries)
                                (first)
                                (get :links))
            response (search/find-concepts-atom :granule {:granule-ur "Granule4"})
            response-links (-> response
                               (get-in [:results :entries])
                               (first)
                               (get :links))]
        (is (= 200 (:status response)))
        (is (= gran-atom-links response-links))
        (is (not-any? #(string/includes? % "s3#") response-links))))

    (testing "testing to ISO 19115."
      (let [gran-atom (da/granules->expected-atom [gran-umm] [coll1] "granules.atom?granule_ur=Granule3")
            response (search/find-metadata :granule :iso19115 {:granule-ur "Granule3"})]
        (is (string/includes? response "s3://aws.com"))))

    (testing "testing to ECHO 10"
      (let [gran-atom (da/granules->expected-atom [gran-umm] [coll1] "granules.atom?granule_ur=Granule3")
            response (search/find-metadata :granule :echo10 {:granule-ur "Granule3"})]
        (is (string/includes? response "s3://aws.com"))))))

(deftest search-granule-stac
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "1970-01-01T12:00:00Z"
                                                :spatial-coverage (dc/spatial {:gsr :geodetic})}))
        coll-concept-id (:concept-id coll1)
        gran-spatial (dg/spatial (m/mbr 10 30 20 0))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :beginning-date-time "2010-01-01T12:00:00.000Z"
                                                   :ending-date-time "2010-01-11T12:00:00.000Z"
                                                   :spatial-coverage gran-spatial
                                                   :cloud-cover 10.0}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"
                                                   :beginning-date-time "2011-02-01T12:00:00.000Z"
                                                   :ending-date-time "2011-02-11T12:00:00.000Z"
                                                   :spatial-coverage gran-spatial
                                                   :cloud-cover 20.0}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule3"
                                                   :beginning-date-time "2012-01-01T12:00:00.000Z"
                                                   :ending-date-time "2012-01-11T12:00:00.000Z"
                                                   :spatial-coverage gran-spatial
                                                   :cloud-cover 30.0}))]

    (index/wait-until-indexed)

    (util/are3 [params result-map]
      (let [response (search/find-concepts-stac
                      :granule
                      (merge params {:collection-concept-id coll-concept-id}))
            post-response (client/post (url/search-url :granule)
                                       {:accept "application/json; profile=stac-catalogue"
                                        :content-type mt/form-url-encoded
                                        :body (codec/form-encode (merge
                                                                  params
                                                                  {:collection-concept-id coll-concept-id}))
                                        :throw-exceptions false
                                        :connection-manager (s/conn-mgr)})
            post-ext-response (client/post (format "%s.stac" (url/search-url :granule))
                                           {:content-type mt/form-url-encoded
                                            :body (codec/form-encode (merge
                                                                      params
                                                                      {:collection-concept-id coll-concept-id}))
                                            :throw-exceptions false
                                            :connection-manager (s/conn-mgr)})
            full-result-map (merge result-map
                                   {:coll-concept-id coll-concept-id
                                    :matched 3
                                    :geometry {:type "Polygon"
                                               :coordinates
                                               [[[10.0 0.0]
                                                 [20.0 0.0]
                                                 [20.0 30.0]
                                                 [10.0 30.0]
                                                 [10.0 0.0]]]}
                                    :bbox [10.0 0.0 20.0 30.0]})]
        (is (search/mime-type-matches-response? response mt/stac))
        (is (= 200 (:status response)))
        (is (= (stac/result-map->expected-stac full-result-map)
               (:results response)))
        (is (search/mime-type-matches-response? post-response mt/stac))
        (is (= 200 (:status post-response)))
        (is (= (stac/result-map->expected-stac (merge
                                                full-result-map
                                                {:body (merge {:collection_concept_id coll-concept-id}
                                                              (-> params
                                                                  util/map-keys->snake_case
                                                                  (dissoc :page_num)
                                                                  (update :page_size str)))})
                                               "POST")
               (json/decode (:body post-response) true)))
        (is (search/mime-type-matches-response? post-ext-response mt/stac))
        (is (= 200 (:status post-ext-response)))
        (is (= (stac/result-map->expected-stac (merge
                                                full-result-map
                                                {:body (merge {:collection_concept_id coll-concept-id}
                                                              (-> params
                                                                  util/map-keys->snake_case
                                                                  (dissoc :page_num)
                                                                  (update :page_size str)))})
                                               "POST")
               (json/decode (:body post-ext-response) true))))

      "default page-size and page-num"
      nil
      {:granules [gran1 gran2 gran3]
       :query-string (format "collection_concept_id=%s" coll-concept-id)
       :page-num 1}

      "specific page-size"
      {:page-size 1}
      {:granules [gran1]
       :query-string (format "page_size=1&collection_concept_id=%s" coll-concept-id)
       :page-num 1
       :next-num 2}

      "specific page-size and page-num"
      {:page-size 1 :page-num 2}
      {:granules [gran2]
       :query-string (format "page_size=1&collection_concept_id=%s" coll-concept-id)
       :page-num 2
       :prev-num 1
       :next-num 3}

      "specific page-size and page-num, no next page"
      {:page-size 2 :page-num 2}
      {:granules [gran3]
       :query-string (format "page_size=2&collection_concept_id=%s" coll-concept-id)
       :page-num 2
       :prev-num 1})

    (testing "stac parameter and query string mix"
      (let [post-response (client/post (format (str (url/search-url :granule) "?provider=PROV1"))
                                       {:accept "application/json; profile=stac-catalogue"
                                        :content-type mt/form-url-encoded
                                        :body (codec/form-encode {:page-size 1 :page-num 2
                                                                  :collection-concept-id coll-concept-id})
                                        :throw-exceptions false
                                        :connection-manager (s/conn-mgr)})
            full-result-map {:coll-concept-id coll-concept-id
                             :granules [gran2]
                             :query-string (format "page_size=1&collection_concept_id=%s" coll-concept-id)
                             :page-num 2
                             :prev-num 1
                             :next-num 3
                             :matched 3
                             :geometry {:type "Polygon"
                                        :coordinates
                                        [[[10.0 0.0]
                                          [20.0 0.0]
                                          [20.0 30.0]
                                          [10.0 30.0]
                                          [10.0 0.0]]]}
                             :bbox [10.0 0.0 20.0 30.0]}]
        (is (search/mime-type-matches-response? post-response mt/stac))
        (is (= 200 (:status post-response)))
        (is (= (stac/result-map->expected-stac (merge
                                                full-result-map
                                                {:body {:collection_concept_id coll-concept-id
                                                        :page_size "1"
                                                        :provider "PROV1"}})
                                               "POST")
               (json/decode (:body post-response) true)))))

    (testing "stac as extension"
      (let [params {:collection-concept-id coll-concept-id}
            response (search/find-concepts-stac :granule
                                                params
                                                {:url-extension "stac"})]
        (is (search/mime-type-matches-response? response mt/stac))
        (is (= (select-keys (search/find-concepts-stac :granule params)
                            [:status :body])
               (select-keys response
                            [:status :body])))))

    (testing "stac search validation"
      (testing "stac search without collection_concept_id"
        (let [response (search/find-concepts-stac :granule {:provider "PROV1"})
              ext-response (search/find-concepts-stac :granule
                                                      {:provider "PROV1"}
                                                      {:url-extension "stac"})]
          (is (= 400
                 (:status response)
                 (:status ext-response)))
          (is (= ["collection_concept_id is required for searching in STAC result format"]
                 (:errors response)
                 (:errors ext-response)))))

      (testing "stac search with unsupported concept type"
        (let [response (search/find-concepts-stac :subscription
                                                  {:collection-concept-id coll-concept-id})
              ext-response (search/find-concepts-stac :subscription
                                                      {:collection-concept-id coll-concept-id}
                                                      {:url-extension "stac"})]
          (is (= 400
                 (:status response)
                 (:status ext-response)))
          (is (= ["STAC result format is only supported for granule searches"]
                 (:errors response)
                 (:errors ext-response)))))

      (testing "stac search with JSON query"
        (let [response (client/post
                        (url/search-url :granule)
                        {:accept "application/json; profile=stac-catalogue"
                         :content-type "application/json"
                         :body (json/generate-string {:collection-concept-id coll-concept-id})
                         :throw-exceptions false
                         :connection-manager (s/conn-mgr)})
              ext-response (client/post
                            (format "%s.stac" (url/search-url :granule))
                            {:content-type "application/json"
                             :body (json/generate-string {:collection-concept-id coll-concept-id})
                             :throw-exceptions false
                             :connection-manager (s/conn-mgr)})]
          (is (= 400
                 (:status response)
                 (:status ext-response)))
          (is (= ["search by JSON query is not allowed with STAC result format"]
                 (:errors (json/decode (:body response) true))
                 (:errors (json/decode (:body ext-response) true))))))

      (testing "stac search with AQL query"
        (let [response (client/post (url/aql-url)
                                    {:accept "application/json; profile=stac-catalogue"
                                     :content-type "application/xml"
                                     :body "<xml></xml>"
                                     :throw-exceptions false
                                     :connection-manager (s/conn-mgr)})
              ext-response (client/post (format "%s.stac" (url/aql-url))
                                        {:content-type "application/xml"
                                         :body "<xml></xml>"
                                         :throw-exceptions false
                                         :connection-manager (s/conn-mgr)})]
          (is (= 400
                 (:status response)
                 (:status ext-response)))
          (is (= ["search by JSON query is not allowed with STAC result format"]
                 (:errors (json/decode (:body response) true))
                 (:errors (json/decode (:body ext-response) true))))))

      (testing "stac search with invalid parameters"
        (util/are3 [params err-message]
          (let [response (search/find-concepts-stac
                          :granule
                          (merge params {:collection-concept-id coll-concept-id}))
                ext-response (search/find-concepts-stac
                              :granule
                              (merge params {:collection-concept-id coll-concept-id})
                              {:url-extension "stac"})]
            (is (= 400
                   (:status response)
                   (:status ext-response)))
            (is (= [err-message]
                   (:errors response)
                   (:errors ext-response))))

          "scroll"
          {:scroll true}
          "scroll is not allowed with STAC result format"

          "offset"
          {:offset 100}
          "offset is not allowed with STAC result format"))

      (testing "stac search with invalid headers"
        (let [{:keys [scroll-id]} (search/find-refs
                                   :granule
                                   {:provider "PROV1" :scroll true :page-size 1})]

          (util/are3 [header-value err-message]
            (let [response (search/find-concepts-stac
                            :granule
                            {:collection-concept-id coll-concept-id}
                            {:headers header-value})
                  ext-response (search/find-concepts-stac
                                :granule
                                {:collection-concept-id coll-concept-id}
                                {:url-extension "stac"
                                 :headers header-value})]
              (is (= 400
                     (:status response)
                     (:status ext-response)))
              (is (= [err-message]
                     (:errors response)
                     (:errors ext-response))))

            "scroll-id"
            {"CMR-Scroll-Id" scroll-id}
            "CMR-Scroll-Id header is not allowed with STAC result format"

            "search-after"
            {"CMR-Search-After" "[0]"}
            "CMR-Search-After header is not allowed with STAC result format"))))))

(deftest granule-concept-stac-retrieval-test
  (let [coll-metadata (slurp (io/resource "stac-test/C1299783579-LPDAAC_ECS.xml"))
        {coll-concept-id :concept-id} (ingest/ingest-concept
                                       (ingest/concept :collection "PROV1" "foo" :echo10 coll-metadata))
        gran-metadata (slurp (io/resource "stac-test/G1327299284-LPDAAC_ECS.xml"))
        {gran-concept-id :concept-id} (ingest/ingest-concept
                                       (ingest/concept :granule "PROV1" "foo" :echo10 gran-metadata))
        expected (-> "stac-test/granule_stac.json"
                     io/resource
                     slurp
                     (string/replace #"CollectionConceptId" coll-concept-id)
                     (string/replace #"GranuleConceptId" gran-concept-id)
                     (json/decode true))]
    (index/wait-until-indexed)

    (testing "retrieval of granule concept in STAC format"
      (util/are3 [options]
        (let [response (search/retrieve-concept gran-concept-id nil options)]
          (is (search/mime-type-matches-response? response mt/stac))
          (is (= expected
                 (json/decode (:body response) true))))

        "retrieval by accept header"
        {:accept "application/json; profile=stac-catalogue"}

        "retrieval by suffix"
        {:url-extension "stac"}))

    (testing "retrieval of granule concept revisions in STAC format is not supported"
      (util/are3 [options err-msg]
        (let [{:keys [status errors]} (search/get-search-failure-data
                                       (search/retrieve-concept
                                        gran-concept-id
                                        1
                                        (merge options {:throw-exceptions true})))]
          (is (= 400 status))
          (is (= [err-msg] errors)))

        "retrieval by accept header"
        {:accept "application/json; profile=stac-catalogue"}
        "The mime types specified in the accept header [application/json; profile=stac-catalogue] are not supported."

        "retrieval by suffix"
        {:url-extension "stac"}
        "The URL extension [stac] is not supported."))))

(deftest search-no-spatial-concept-stac
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "Dataset1"
                                                :beginning-date-time "1970-01-01T12:00:00Z"}))
        coll-concept-id (:concept-id coll1)
        gran-spatial (dg/spatial (m/mbr 10 30 20 0))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                   :beginning-date-time "2010-01-01T12:00:00.000Z"
                                                   :ending-date-time "2010-01-11T12:00:00.000Z"
                                                   :cloud-cover 10.0}))
        gran-concept-id (:concept-id gran1)
        expected-gran-err-msg (format "Granule [%s] without spatial info is not supported in STAC"
                                      gran-concept-id)]

    (index/wait-until-indexed)

    (testing "granule search in STAC returns error when there is granule without spatial info"
      (let [response (search/find-concepts-stac
                      :granule
                      {:collection-concept-id coll-concept-id})
            ext-response (search/find-concepts-stac
                          :granule
                          {:collection-concept-id coll-concept-id}
                          {:url-extension "stac"})]
        (is (= 400
               (:status response)
               (:status ext-response)))
        (is (= [expected-gran-err-msg]
               (:errors response)
               (:errors ext-response)))))

    (testing "granule retrieval in STAC returns error when granule has no spatial info"
      (util/are3 [options]
        (let [{:keys [status errors]} (search/get-search-failure-data
                                       (search/retrieve-concept
                                        gran-concept-id
                                        nil
                                        (merge options {:throw-exceptions true})))]
          (is (= 400 status))
          (is (= [expected-gran-err-msg] errors)))

        "retrieval by accept header"
        {:accept "application/json; profile=stac-catalogue"}

        "retrieval by suffix"
        {:url-extension "stac"}))

    (testing "collection retrieval in STAC returns error when collection has no spatial info"
      (util/are3 [options]
        (let [expected-err-msg (format "Collection [%s] without spatial info is not supported in STAC"
                                       coll-concept-id)
              {:keys [status errors]} (search/get-search-failure-data
                                       (search/retrieve-concept
                                        coll-concept-id
                                        nil
                                        (merge options {:throw-exceptions true})))]
          (is (= 400 status))
          (is (= [expected-err-msg] errors)))

        "retrieval by accept header"
        {:accept "application/json; profile=stac-catalogue"}

        "retrieval by suffix"
        {:url-extension "stac"}))))

(deftest combined-cloud-cover-stac-test
  (let [coll-metadata (slurp (io/resource "stac-test/C2021957295-LPCLOUD.umm_json"))
        {coll-concept-id :concept-id} (ingest/ingest-concept
                                       (ingest/concept :collection
                                                       "PROV1"
                                                       "foo"
                                                       {:format :umm-json :version "1.17.0"}
                                                       coll-metadata))
        {aa-gran-concept-id :concept-id} (ingest/ingest-concept
                                          (ingest/concept :granule
                                                          "PROV1"
                                                          "aa"
                                                          :echo10
                                                          (slurp (io/resource "stac-test/G2485638210-LPCLOUD-aa.echo10"))))
        {root-gran-concept-id :concept-id} (ingest/ingest-concept
                                            (ingest/concept :granule
                                                            "PROV1"
                                                            "root"
                                                            :echo10
                                                            (slurp (io/resource "stac-test/G2485638210-LPCLOUD-root.echo10"))))
        {neither-gran-concept-id :concept-id} (ingest/ingest-concept
                                               (ingest/concept :granule
                                                               "PROV1"
                                                               "neither"
                                                               :echo10
                                                               (slurp (io/resource "stac-test/G2485638210-LPCLOUD-neither.echo10"))))
        {both-gran-concept-id :concept-id} (ingest/ingest-concept
                                            (ingest/concept :granule
                                                            "PROV1"
                                                            "both"
                                                            :echo10
                                                            (slurp (io/resource "stac-test/G2485638210-LPCLOUD-both.echo10"))))]

    (index/wait-until-indexed)
    (testing "combined cloud cover is STAC format aa only"
      (let [response (search/retrieve-concept aa-gran-concept-id nil {:url-extension "stac"})]
        (is (= 1
               (:eo:cloud_cover (:properties (json/decode (:body response) true)))))))

    (testing "combined cloud cover is STAC format root only"
      (let [response (search/retrieve-concept root-gran-concept-id nil {:url-extension "stac"})]
        (is (= 2.0
               (:eo:cloud_cover (:properties (json/decode (:body response) true)))))))

    (testing "combined cloud cover is STAC format neither"
      (let [response (search/retrieve-concept neither-gran-concept-id nil {:url-extension "stac"})]
        (is (= nil
               (:eo:cloud_cover (:properties (json/decode (:body response) true)))))))

    (testing "combined cloud cover is STAC format both"
      (let [response (search/retrieve-concept both-gran-concept-id nil {:url-extension "stac"})]
        (is (= 2.0
               (:eo:cloud_cover (:properties (json/decode (:body response) true)))))))))
