(ns cmr.system-int-test.search.harvesting-test
  "Tests for using the scroll parameter to harvest metadata"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.system-int-test.data2.collection :as data2-collection]
   [cmr.system-int-test.data2.core :as data2-core]
   [cmr.system-int-test.data2.granule :as data2-granule]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-system-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    dev-system-util/resume-time-fixture!]))

(deftest harvest-granules
  (let [format-key :echo10
        _ (dev-system-util/freeze-time! "2010-01-01T10:00:00Z")
        coll1-echo (data2-core/ingest "PROV1" (data2-collection/collection) {:format :echo10})
        coll1-concept-id (:concept-id coll1-echo)

        _ (dev-system-util/freeze-time! "2012-01-01T10:00:00Z")
        coll2-echo (data2-core/ingest "PROV1" (data2-collection/collection) {:format :echo10})
        coll2-concept-id (:concept-id coll2-echo)

        _ (dev-system-util/freeze-time! "2010-01-01T10:00:00Z")
        g1-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll1-echo {:granule-ur "g1"
                                                                      :producer-gran-id "p1"})
                                   {:format :echo10})
        _ (dev-system-util/freeze-time! "2011-01-01T10:00:00Z")
        g2-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll1-echo {:granule-ur "g2"
                                                                      :producer-gran-id "p2"})
                                   {:format :echo10})
        _ (dev-system-util/freeze-time! "2012-01-01T10:00:00Z")
        g3-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll1-echo {:granule-ur "g3"
                                                                      :producer-gran-id "p3"})
                                   {:format :echo10})
        _ (dev-system-util/freeze-time! "2013-01-01T10:00:00Z")
        g4-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll1-echo {:granule-ur "g4"
                                                                      :producer-gran-id "p4"})
                                   {:format :echo10})
        _ (dev-system-util/freeze-time! "2014-01-01T10:00:00Z")
        g5-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll1-echo {:granule-ur "g5"
                                                                      :producer-gran-id "p5"})
                                   {:format :echo10})
        _ (dev-system-util/freeze-time! "2015-01-01T10:00:00Z")
        g6-echo (data2-core/ingest "PROV1"
                                   (data2-granule/granule coll2-echo {:granule-ur "g6"
                                                                      :producer-gran-id "p6"})
                                   {:format :echo10})
        coll1-grans [g1-echo g2-echo g3-echo g4-echo g5-echo]]

    (index/wait-until-indexed)

    (testing "scroll"
      (let [params {:collection_concept_id (:concept-id coll1-echo) :scroll true :page-size 2}
            options {:accept nil
                     :url-extension "native"}
            response (search/find-metadata :granule :json params options)
            scroll-id (:scroll-id response)]
        (testing "First search gets expected count and scroll-id"
          (is (= (count coll1-grans) (:hits response)))
          (is (not (nil? scroll-id)))))

      (testing "Harvest by collection-concept-id"
        (let [params {:collection_concept_id (:concept-id coll1-echo) :scroll true :page-size 2}
              options {:accept nil
                       :url-extension "native"}
              response (search/find-metadata :granule format-key params options)
              scroll-id (:scroll-id response)]
          (testing "First search gets expected granules and scroll-id"
            (is (= (count coll1-grans) (:hits response)))
            (is (not (nil? scroll-id)))
            (data2-core/assert-metadata-results-match format-key [g1-echo g2-echo] response))

          (testing "Second search gets next two granules"
            (data2-core/assert-metadata-results-match
             format-key
             [g3-echo g4-echo]
             (search/find-metadata :granule
                                   format-key
                                   {:scroll true}
                                   {:headers {"CMR-Scroll-Id" scroll-id}})))

          (testing "Third search gets last granule"
            (data2-core/assert-metadata-results-match
             format-key
             [g5-echo]
             (search/find-metadata :granule
                                   format-key
                                   {:scroll true}
                                   {:headers {"CMR-Scroll-Id" scroll-id}})))

          (testing "Subsequent search gets empty list"
            (data2-core/assert-metadata-results-match
             format-key
             []
             (search/find-metadata :granule
                                   format-key
                                   {:scroll true}
                                   {:headers {"CMR-Scroll-Id" scroll-id}})))

          (testing "scroll-id is not allowed with search-after"
            (let [{:keys [status errors]} (search/find-metadata :granule
                                                 format-key
                                                 {}
                                                 {:headers {"CMR-Scroll-Id" scroll-id
                                                            "CMR-Search-After" "[0]"}})]
              (is (= 400 status))
              (is (= ["scroll_id is not allowed with search-after"]
                     errors))))))

      ;; Created-at is controlled by the Oracle database server normally. With the in-memory
      ;; database we use time-keeper so that we can set created-at dates explicitly
      (s/only-with-in-memory-database
       (testing "Harvest granules by created-at"
         (let [params {:concept-id [coll1-concept-id coll2-concept-id]
                       :created-at "2010-01-01T10:00:00Z,2014-02-01T10:00:00Z"
                       :scroll true
                       :page-size 2}
               options {:accept nil
                        :url-extension "native"}
               response (search/find-metadata :granule :echo10 params options)
               scroll-id (:scroll-id response)]
           (is (not (nil? scroll-id)))
           (data2-core/assert-metadata-results-match format-key [g1-echo g2-echo] response)))
       (testing "Harvest collections by created-at"
         (let [params {:created_at "2010-01-01T10:00:00Z,2011-01-01T10:00:00Z" :scroll true :page-size 2}
               options {:accept nil
                        :url-extension "native"}
               response (search/find-metadata :collection :echo10 params options)
               scroll-id (:scroll-id response)]
           (is (= 1 (:hits response)))
           (is (not (nil? scroll-id)))
           (data2-core/assert-metadata-results-match format-key [coll1-echo] response)))))

    (testing "search-after"
      (testing "Harvest by collection-concept-id"
        (let [params {:collection_concept_id (:concept-id coll1-echo) :page-size 2}
              options {:accept nil
                       :url-extension "native"}
              response (search/find-metadata :granule format-key params options)
              search-after (:search-after response)]
          (testing "First search gets expected granules and search-after"
            (is (= (count coll1-grans) (:hits response)))
            (is (not (nil? search-after)))
            (data2-core/assert-metadata-results-match
             format-key
             [g1-echo g2-echo]
             response))

          (testing "Second search gets next two granules and search-after"
            (let [response (search/find-metadata :granule
                                                 format-key
                                                 params
                                                 {:headers {"CMR-Search-After" search-after}})
                  search-after-1 (:search-after response)]
              (is (not (nil? search-after-1)))
              (is (not= search-after search-after-1))
              (data2-core/assert-metadata-results-match
               format-key
               [g3-echo g4-echo]
               response)

              (testing "Third search gets last granule and search-after"
                (let [response (search/find-metadata :granule
                                                     format-key
                                                     params
                                                     {:headers {"CMR-Search-After" search-after-1}})
                      search-after-2 (:search-after response)]
                  (is (not (nil? search-after-2)))
                  (is (not= search-after search-after-1 search-after-2))
                  (data2-core/assert-metadata-results-match
                   format-key
                   [g5-echo]
                   response)

                  (testing "Subsequent search gets empty list and no search-after"
                    (let [response (search/find-metadata :granule
                                                         format-key
                                                         params
                                                         {:headers {"CMR-Search-After" search-after-2}})
                          search-after-3 (:search-after response)]
                      (is (nil? search-after-3))
                      (data2-core/assert-metadata-results-match
                       format-key
                       []
                       response)))))))))

      ;; Created-at is controlled by the Oracle database server normally. With the in-memory
      ;; database we use time-keeper so that we can set created-at dates explicitly
      (s/only-with-in-memory-database
       (testing "Harvest granules by created-at"
         (let [params {:concept-id [coll1-concept-id coll2-concept-id]
                       :created-at "2010-01-01T10:00:00Z,2014-02-01T10:00:00Z"
                       :page-size 2}
               options {:accept nil
                        :url-extension "native"}
               response (search/find-metadata :granule :echo10 params options)
               search-after (:search-after response)]
           (is (not (nil? search-after)))
           (data2-core/assert-metadata-results-match format-key [g1-echo g2-echo] response))
         (testing "Harvest collections by created-at"
           (let [params {:created_at "2010-01-01T10:00:00Z,2011-01-01T10:00:00Z" :page-size 2}
                 options {:accept nil
                          :url-extension "native"}
                 response (search/find-metadata :collection :echo10 params options)
                 search-after (:search-after response)]
             (is (= 1 (:hits response)))
             (is (not (nil? search-after)))
             (data2-core/assert-metadata-results-match format-key [coll1-echo] response))))))))
