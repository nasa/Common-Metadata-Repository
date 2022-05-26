(ns cmr.system-int-test.site.holdings-provider-test
  "Integration tests for provider holdings page."
  (:require
   [clojure.test :refer :all]
   [clojure.string :refer [trim]]
   [cmr.mock-echo.client.echo-util :as echo]
   [cmr.system-int-test.data2.core :as data]
   [cmr.system-int-test.data2.granule :as data-granule]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.html-helper :refer [find-element-by-type find-element-by-id]]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.tag-util :as tags]
   [cmr.system-int-test.utils.url-helper :as url]
   [crouton.html :as html]))

(use-fixtures :each (join-fixtures
                     [(ingest/reset-fixture {"provguid1" "PROV1"}
                                            {:grant-all-ingest? true
                                             :grant-all-search? true
                                             :grant-all-access-control? false})
                      tags/grant-all-tag-fixture]))

(deftest provider-holdings-test
  (let [coll1 (data/ingest-umm-spec-collection
               "PROV1"
               (data-umm-c/collection {:concept-id "C1-PROV1"
                                       :TemporalExtents
                                       [(data-umm-cmn/temporal-extent
                                         {:beginning-date-time "1970-01-01T00:00:00Z"})]}))
        coll2 (data/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection
                        {:concept-id "C2-PROV1"
                         :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                         :EntryTitle "C2-PROV1-et"
                         :ShortName "C2-PROV1-sn"}))

        coll3 (data/ingest-umm-spec-collection
               "PROV1" (data-umm-c/collection
                        {:concept-id "C3-PROV1"
                         :SpatialExtent (data-umm-c/spatial {:gsr "GEODETIC"})
                         :EntryTitle "C3-PROV1-mt"
                         :ShortName "granule-less collection"}))
        _ (index/wait-until-indexed)

        _g1 (data/ingest "PROV1" (data-granule/granule-with-umm-spec-collection
                                  coll1 (:concept-id coll1)
                                  {:granule-ur "Granule1"
                                   :beginning-date-time "1970-06-02T12:00:00Z"
                                   :ending-date-time "1975-02-02T12:00:00Z"}))

        _g1-online-1 (data/ingest "PROV1" (data-granule/granule-with-umm-spec-collection
                                           coll1 (:concept-id coll1)
                                           {:related-urls [(data-granule/related-url {:type "GET DATA"})]
                                            :beginning-date-time "1970-06-02T12:00:00Z"
                                            :ending-date-time "1975-02-02T12:00:00Z"}))

        _g1-online-2 (data/ingest "PROV1" (data-granule/granule-with-umm-spec-collection
                                           coll1 (:concept-id coll1)
                                           {:related-urls [(data-granule/related-url {:type "GET DATA"})]
                                            :beginning-date-time "1970-06-02T12:00:00Z"
                                            :ending-date-time "1975-02-02T12:00:00Z"}))

        _g2 (data/ingest "PROV1"
                         (data-granule/granule-with-umm-spec-collection
                          coll2 (:concept-id coll2)
                          {:spatial-coverage (data-granule/spatial-with-track
                                              {:cycle 1
                                               :passes [{:pass 1}]})
                           :beginning-date-time "2012-01-01T00:00:00.000Z"
                           :ending-date-time "2012-01-01T00:00:00.000Z"})
                         {:format :umm-json})

        _g3 (data/ingest "PROV1"
                         (data-granule/granule-with-umm-spec-collection
                          coll2 (:concept-id coll2)
                          {:spatial-coverage (data-granule/spatial-with-track
                                              {:cycle 2
                                               :passes [{:pass 3}
                                                        {:pass 4}]})
                           :beginning-date-time "2012-01-01T00:00:00.000Z"
                           :ending-date-time "2012-01-01T00:00:00.000Z"})
                         {:format :umm-json})

        _g3-online (data/ingest "PROV1"
                                (data-granule/granule-with-umm-spec-collection
                                 coll2 (:concept-id coll2)
                                 {:spatial-coverage (data-granule/spatial-with-track
                                                     {:cycle 2
                                                      :passes [{:pass 3}
                                                               {:pass 4}]})
                                  :related-urls [(data-granule/related-url {:type "GET DATA"})
                                                 (data-granule/related-url)]
                                  :beginning-date-time "2012-01-01T00:00:00.000Z"
                                  :ending-date-time "2012-01-01T00:00:00.000Z"})
                                {:format :umm-json})

        _ (index/wait-until-indexed)
        user1-token (echo/login (system/context) "user1")
        tag1-colls [coll1 coll2 coll3]
        tag-key "tag1"]

    (tags/save-tag
     user1-token
     (tags/make-tag {:tag-key tag-key})
     tag1-colls)
    (index/wait-until-indexed)

    (testing "Page renders"
      (let [page-data (html/parse (format "%sPROV1/tag1"
                                          (url/search-site-providers-holdings-url)))]
        (is (not= nil page-data))

        (testing "Virtual directory links exist for each collection."
          (is (= 2
                 (->> page-data
                      (find-element-by-type :a)
                      (filter #(re-matches #".*collections/C\d-PROV\d.*"
                                           (get-in % [:attrs :href])))
                      count))))

        (testing "Collection granule counts and pluralizations are correct."
          (is (= "Browse 1 Granule"
                 (->> page-data
                      (find-element-by-id "C2-PROV1-virtual-directory-link")
                      :content
                      first
                      trim)))

          (is (= "Browse 2 Granules"
                 (->> page-data
                      (find-element-by-id "C1-PROV1-virtual-directory-link")
                      :content
                      first
                      trim))))

        (testing "Collections with no granules do not have link"
          (is (= nil (find-element-by-id "C3-PROV1-virtual-directory-link" page-data)))
          (is (= "No Granules"
                 (->> page-data
                      (find-element-by-id "C3-PROV1-no-granules-msg")
                      :content
                      first
                      trim))))))))

