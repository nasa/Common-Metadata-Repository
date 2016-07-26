(ns cmr.system-int-test.search.umm-json-search-test
  "Integration test for UMMJSON format search"
  (:require [clojure.test :refer :all]
            [cheshire.core :as json]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.test.location-keywords-helper :as lkt]
            [cmr.umm-spec.versioning :as umm-version]
            [cmr.umm-spec.json-schema :as umm-json-schema]
            [cmr.umm.core :as umm-lib]
            [cmr.spatial.point :as p]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util :refer [are3]]
            [cmr.umm.collection.entry-id :as eid]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(def test-context (lkt/setup-context-for-test lkt/sample-keyword-map))

(defn- collection->umm-json-meta
  "Returns the meta section of umm-json format."
  [collection]
  (let [{:keys [entry-title user-id format-key
                revision-id concept-id provider-id deleted]} collection]
    (util/remove-nil-keys
     {:concept-type "collection"
      :concept-id concept-id
      :revision-id revision-id
      :native-id entry-title
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type format-key)
      :deleted (boolean deleted)})))

(defn- collection->legacy-umm-json
  "Returns the response of a search in legacy UMM JSON format. The UMM JSON search response format was
   originally created with a umm field which contained a few collection fields but was not UMM JSON."
  [collection]
  (let [{{:keys [short-name version-id]} :product
         :keys [entry-title]} collection]
    {:meta (collection->umm-json-meta collection)
     :umm {:entry-id (eid/entry-id short-name version-id)
           :entry-title entry-title
           :short-name short-name
           :version-id version-id}}))

(defn- assert-legacy-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (is (= (set (map collection->legacy-umm-json collections))
           (set (map #(util/dissoc-in % [:meta :revision-date])
                     (get-in search-result [:results :items])))))))

(defn- collection->umm-json
  "Returns the response of a search in UMM JSON format."
  [version collection]
  (if (:deleted collection)
    {:meta (collection->umm-json-meta collection)}
    (let [ingested-metadata (umm-lib/umm->xml collection (:format-key collection))
          umm-spec-record (umm-spec/parse-metadata
                           test-context :collection (:format-key collection) ingested-metadata)
          umm-json (umm-spec/generate-metadata
                    test-context umm-spec-record {:format :umm-json :version version})]
      {:meta (collection->umm-json-meta collection)
       :umm (json/decode umm-json true)})))

(defn- assert-umm-jsons-match
  "Returns true if the UMM collection umm-jsons match the umm-jsons returned from the search."
  [version collections search-result]
  (if (and (some? (:status search-result)) (not= 200 (:status search-result)))
    (is (= 200 (:status search-result)) (pr-str search-result))
    (do
      ;; TODO check that the content type header is correct
      (is (nil? (util/seqv (umm-json-schema/validate-umm-json-search-result (:body search-result) version)))
          "UMM search result JSON was invalid")
      (is (= (set (map #(collection->umm-json version %) collections))
             (set (map #(util/dissoc-in % [:meta :revision-date])
                       (get-in search-result [:results :items]))))))))

(defn umm-collection
  "Creates a minimal valid UMM collection"
  [attribs]
  (dc/collection
   (merge
    {:platforms [(dc/platform {:short-name "platform"})]
     :processing-level-id "processing"
     :related-urls [(dc/related-url {:type "GET DATA"})]
     :science-keywords [(dc/science-keyword {:category "Cat1"
                                             :topic "Topic1"
                                             :term "Term1"})]
     :spatial-coverage (dc/spatial {:gsr :geodetic, :sr :geodetic, :geometries [p/north-pole]})
     :beginning-date-time "2000-01-01T00:00:00Z"}
    attribs)))

(deftest search-collection-umm-json
  (let [coll1-1 (d/ingest "PROV1" (umm-collection {:entry-title "et1"
                                                   :version-id "v1"
                                                   :short-name "s1"})
                          {:user-id "user1"})
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge coll1-1
                                 {:deleted true :user-id "user2"}
                                 (ingest/delete-concept concept1 {:user-id "user2"}))
        coll1-3 (d/ingest "PROV1" (umm-collection {:entry-title "et1"
                                                   :version-id "v2"
                                                   :short-name "s1"}))

        coll2-1 (d/ingest "PROV1" (umm-collection {:entry-title "et2"
                                                   :version-id "v1"
                                                   :short-name "s2"}))
        coll2-2 (d/ingest "PROV1" (umm-collection {:entry-title "et2"
                                                   :version-id "v2"
                                                   :short-name "s2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll2-2)}
        coll2-3-tombstone (merge coll2-2 {:deleted true} (ingest/delete-concept concept2))

        coll3 (d/ingest "PROV2" (umm-collection {:entry-title "et3"
                                                 :version-id "v4"
                                                 :short-name "s1"})
                        {:user-id "user3"})]
    (index/wait-until-indexed)
    (testing "find collections in legacy UMM JSON format"
      (are3 [url-extension collections params]
        (assert-legacy-umm-jsons-match
         collections (search/find-concepts-umm-json :collection params {:url-extension url-extension}))

        ;; We don't want to break existing clients so we allow the .umm-json url extension to continue to work
        ".umm-json url extension, all-revisions=false"
        "umm-json"
        [coll1-3 coll3]
        {:all-revisions false}

        ".legacy-umm-json url extension, all-revisions=false"
        "legacy-umm-json"
        [coll1-3 coll3]
        {:all-revisions false}

        ".legacy-umm-json url extension, all-revisions true"
        "legacy-umm-json"
        [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
        {:all-revisions true}))

    ;; TODO write API docs for all this stuff.

    (testing "finding collections in different versions of UMM JSON"
      (are3 [version accept-header url-extension]
        (let [options (if accept-header
                        {:accept accept-header}
                        {:url-extension url-extension})
              collections [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
              response (search/find-concepts-umm-json :collection {:all-revisions true} options)]
          (assert-umm-jsons-match version collections response))
        "Latest version is default with accept header of UMM JSON Search Results"
        umm-version/current-version mt/umm-json-results nil
        "Latest version is default with accept header of UMM JSON"
        umm-version/current-version mt/umm-json nil
        "Retrieve older version with accept header of UMM JSON Search Results"
        "1.3" (str mt/umm-json-results ";version=1.3") nil
        "Retrieve older version with accept header of UMM JSON"
        "1.3" (str mt/umm-json ";version=1.3") nil

        ;; TODO add test of unrecognized umm json version and generally invalid ones.
        "Retrieve specified version 1.3 with URL extension"
        "1.3" nil "umm_json_v1_3"
        "Retrieve specified version 1.0 with URL extension"
        "1.0" nil "umm_json_v1_0"))


    (testing "find collections in umm-json format"
      (are3 [collections params]
        (assert-umm-jsons-match
         umm-version/current-version collections
         (search/find-concepts-umm-json :collection params))

        ;; Should not get matching tombstone for second collection back
        "provider-id all-revisions=false"
        [coll1-3]
        {:provider-id "PROV1" :all-revisions false}

        "provider-id all-revisions unspecified"
        [coll1-3]
        {:provider-id "PROV1"}

        "provider-id all-revisions=true"
        [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone]
        {:provider-id "PROV1" :all-revisions true}

        "native-id all-revisions=false"
        [coll1-3]
        {:native-id "et1" :all-revisions false}

        "native-id all-revisions unspecified"
        [coll1-3]
        {:native-id "et1"}

        "native-id all-revisions=true"
        [coll1-1 coll1-2-tombstone coll1-3]
        {:native-id "et1" :all-revisions true}

        "version all-revisions=false"
        [coll1-3]
        {:version "v2" :all-revisions false}

        "version all-revisions unspecified"
        [coll1-3]
        {:version "v2"}

        "version all-revisions=true"
        [coll1-3 coll2-2 coll2-3-tombstone]
        {:version "v2" :all-revisions true}

        ;; verify that "finding latest", i.e., all-revisions=false, does not return old revisions
        "version all-revisions=false - no match to latest"
        []
        {:version "v1" :all-revisions false}

        "short-name all-revisions false"
        [coll1-3 coll3]
        {:short-name "s1" :all-revisions false}

        ;; this test is across providers
        "short-name all-revisions unspecified"
        [coll1-3 coll3]
        {:short-name "s1"}

        "short-name all-revisions true"
        [coll1-1 coll1-2-tombstone coll1-3 coll3]
        {:short-name "s1" :all-revisions true}

        "concept-id all-revisions false"
        [coll1-3]
        {:concept-id "C1200000000-PROV1" :all-revisions false}

        "concept-id all-revisions unspecified"
        [coll1-3]
        {:concept-id "C1200000000-PROV1"}

        "concept-id all-revisions true"
        [coll1-1 coll1-2-tombstone coll1-3]
        {:concept-id "C1200000000-PROV1" :all-revisions true}

        "all-revisions true"
        [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
        {:all-revisions true}))))



(deftest search-umm-json-error-cases
  (testing "granule umm-json search is not supported"
    (let [{:keys [status errors]} (search/find-concepts-umm-json :granule {})]
      (is (= [400 ["The mime type [application/vnd.nasa.cmr.umm_results+json] is not supported for granules."]]
             [status errors])))))

