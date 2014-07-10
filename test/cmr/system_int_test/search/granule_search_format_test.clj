(ns cmr.system-int-test.search.granule-search-format-test
  "Integration tests for searching granules in csv format"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.url-helper :as url]
            [clj-http.client :as client]
            [cmr.common.concepts :as cu]
            [cmr.umm.core :as umm]
            [cmr.umm.related-url-helper :as ru]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1"))

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
        ru2 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {}))
        gran1 (d/ingest "CMR_PROV1" (dg/granule coll1 {:granule-ur "Granule1"
                                                       :beginning-date-time "2010-01-01T12:00:00Z"
                                                       :ending-date-time "2010-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #1"
                                                       :day-night "DAY"
                                                       :size 100
                                                       :cloud-cover 50
                                                       :related-urls [ru1 ru2]}))
        gran2 (d/ingest "CMR_PROV1" (dg/granule coll2 {:granule-ur "Granule2"
                                                       :beginning-date-time "2011-01-01T12:00:00Z"
                                                       :ending-date-time "2011-01-11T12:00:00Z"
                                                       :producer-gran-id "Granule #2"
                                                       :day-night "NIGHT"
                                                       :size 80
                                                       :cloud-cover 30}))]

    (index/refresh-elastic-index)

    (let [response (search/find-grans-csv :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,http://example.com,http://example.com/browse,50.0,DAY,100.0\n")
             (:body response))))
    (let [response (search/find-grans-csv :granule {})]
      (is (= 200 (:status response)))
      (is (= (str "Granule UR,Producer Granule ID,Start Time,End Time,Online Access URLs,Browse URLs,Cloud Cover,Day/Night,Size\n"
                  "Granule1,Granule #1,2010-01-01T12:00:00Z,2010-01-11T12:00:00Z,http://example.com,http://example.com/browse,50.0,DAY,100.0\n"
                  "Granule2,Granule #2,2011-01-01T12:00:00Z,2011-01-11T12:00:00Z,,,30.0,NIGHT,80.0\n")
             (:body response))))

    (testing "as extension"
      (is (= (select-keys (search/find-grans-csv :granule {:granule-ur "Granule1"})
                          [:status :body])
             (select-keys (search/find-grans-csv :granule
                                                 {:granule-ur "Granule1"}
                                                 {:format-as-ext? true})
                          [:status :body]))))))

(def resource-type->link-type-uri
  {"GET DATA" "http://esipfed.org/ns/fedsearch/1.1/data#"
   "GET RELATED VISUALIZATION" "http://esipfed.org/ns/fedsearch/1.1/browse#"})

(defn- add-attribs
  "Returns the attribs with the field-value pair added if there is a value"
  [attribs field value]
  (if (empty? value) attribs (assoc attribs field value)))

(defn- related-url->link
  "Returns the atom link of the given related url"
  [related-url]
  (let [{:keys [type url title mime-type size]} related-url
        attribs (-> {}
                    (add-attribs :size size)
                    (add-attribs :rel (resource-type->link-type-uri type))
                    (add-attribs :type mime-type)
                    (add-attribs :title title)
                    (add-attribs :hreflang "en-US")
                    (add-attribs :href url))]
    attribs))

(defn- related-urls->links
  "Returns the atom links of the given related urls"
  [related-urls]
  (map related-url->link related-urls))

(defn- granule->expected-atom
  "Returns the atom map of the granule"
  [granule]
  (let [{:keys [concept-id granule-ur producer-gran-id size related-urls
                beginning-date-time ending-date-time day-night cloud-cover]} granule
        dataset-id (get-in granule [:collection-ref :entry-title])]
    {:id concept-id
     :title granule-ur
     :dataset-id dataset-id
     :producer-granule-id producer-gran-id
     :size (str size)
     ;; TODO original-format will be changed to ECHO10 later once the metadata-db format is updated to ECHO10
     :original-format "application/echo10+xml"
     :data-center (:provider-id (cu/parse-concept-id concept-id))
     :links (related-urls->links related-urls)
     :start beginning-date-time
     :end ending-date-time
     :online-access-flag (str (> (count (ru/downloadable-urls related-urls)) 0))
     :browse-flag (str (> (count (ru/browse-urls related-urls)) 0))
     :day-night-flag day-night
     :cloud-cover (str cloud-cover)}))

(defn- granules->expected-atom
  "Returns the atom map of the granules"
  [granules atom-path]
  {:id (str (url/search-root) atom-path)
   :title "ECHO granule metadata"
   :entries (map granule->expected-atom granules)})

(deftest search-granule-atom
  (let [ru1 (dc/related-url "GET DATA" "http://example.com")
        ru2 (dc/related-url "GET DATA" "http://example2.com")
        ru3 (dc/related-url "GET RELATED VISUALIZATION" "http://example.com/browse")
        coll1 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset1"}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:entry-title "Dataset2"}))
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
                                                       :related-urls [ru3]}))]

    (index/refresh-elastic-index)

    (let [gran-atom (granules->expected-atom [gran1] "granules.atom?granule-ur=Granule1")
          response (search/find-grans-atom :granule {:granule-ur "Granule1"})]
      (is (= 200 (:status response)))
      (is (= gran-atom
             (:results response))))

    (let [gran-atom (granules->expected-atom [gran1 gran2] "granules.atom")
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
               [:status :results]))))))

