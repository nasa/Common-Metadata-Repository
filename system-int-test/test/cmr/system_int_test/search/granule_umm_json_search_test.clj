(ns cmr.system-int-test.search.granule-umm-json-search-test
  "Integration tests for searching granules in UMM JSON format"
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.versioning :as versioning]
   [cmr.umm.umm-granule :as umm-g]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(defn- ingest-granule-concept
  "Returns granule concept in the given parent collection, entry title, granule ur
  and metadata format. Metadata format is in keyword form, e.g. :echo10, :iso-smap, :umm-json."
  [collection entry-title granule-ur metadata-format]
  (let [granule (dg/granule-with-umm-spec-collection
                 collection
                 (:concept-id collection)
                 {:granule-ur granule-ur
                  :collection-ref (umm-g/map->CollectionRef {:entry-title entry-title})})
        gran-concept (d/item->concept granule metadata-format)
        umm-g-gran (d/item->concept granule :umm-json)
        {:keys [concept-id revision-id status] :as response} (ingest/ingest-concept gran-concept)]
    (if (#{200 201} status)
      (merge umm-g-gran response {:format metadata-format})
      response)))

(defn assert-umm-json-found
  ([granules version]
   (assert-umm-json-found granules version nil))
  ([granules version params]
   (let [params (merge {:concept-id (map :concept-id granules)} params)
         options {:accept (mt/with-version mt/umm-json-results version)}
         response (search/find-concepts-umm-json :granule params options)]
     (du/assert-granule-umm-jsons-match version granules response))))

(deftest search-granule-in-umm-json-test
  (let [coll1-entry-title "coll1"
        collection (d/ingest-umm-spec-collection
                    "PROV1"
                    (data-umm-c/collection {:EntryTitle coll1-entry-title}))
        gran-to-be-deleted (d/ingest "PROV1"
                                     (dg/granule-with-umm-spec-collection
                                      collection (:concept-id collection)))
        echo10-gran (ingest-granule-concept collection coll1-entry-title "echo10-gran" :echo10)
        smap-gran (ingest-granule-concept collection coll1-entry-title "iso-smap-gran" :iso-smap)
        umm-g-gran (ingest-granule-concept collection coll1-entry-title "umm-g-gran" :umm-json)]
    ;; delete a granule and verify that the deleted granule is not found
    (ingest/delete-concept (d/item->concept gran-to-be-deleted :echo10))
    (index/wait-until-indexed)
    (testing "search granules in UMM JSON format"
      (assert-umm-json-found
       [echo10-gran smap-gran umm-g-gran] versioning/current-granule-version {:provider "PROV1"}))

    (testing "search granules with invalid UMM JSON version"
      (let [expected-errors [(str "The mime type [application/vnd.nasa.cmr.umm_results+json] "
                                  "with version [1.0] is not supported for granules.")]]

        (testing "invalid UMM JSON version via suffix"
          (let [{:keys [status errors]} (search/find-concepts-umm-json
                                         :granule {} {:url-extension "umm_json_v1_0"})]
            (is (= [400 expected-errors]
                   [status errors]))))

        (testing "invalid UMM JSON version via accept header"
          (let [{:keys [status errors]} (search/find-concepts-umm-json
                                         :granule {}
                                         {:accept (mt/with-version mt/umm-json "1.0")})]
            (is (= [400 expected-errors]
                   [status errors]))))))))
