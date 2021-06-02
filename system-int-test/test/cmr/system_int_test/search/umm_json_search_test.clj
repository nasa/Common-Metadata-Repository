(ns cmr.system-int-test.search.umm-json-search-test
  "Integration test for UMM-JSON format search"
  (:require
   [clojure.test :refer :all]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util :refer [are3]]
   [cmr.spatial.point :as p]
   [cmr.system-int-test.data2.collection :as dc]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-json :as du]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.umm-spec.versioning :as umm-version]))


(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))


(deftest search-collection-umm-json
  (let [coll1-1 (d/ingest "PROV1" (du/umm-spec-collection {:entry-title "et1"
                                                           :version-id "v1"
                                                           :short-name "s1"})
                          {:user-id "user1"})
        concept1 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll1-1)}
        coll1-2-tombstone (merge coll1-1
                                 {:deleted true :user-id "user2"}
                                 (ingest/delete-concept concept1 {:user-id "user2"}))
        coll1-3 (d/ingest "PROV1" (du/umm-spec-collection {:entry-title "et1"
                                                           :version-id "v2"
                                                           :short-name "s1"}))

        coll2-1 (d/ingest "PROV1" (du/umm-spec-collection {:entry-title "et2"
                                                           :version-id "v1"
                                                           :short-name "s2"}))
        coll2-2 (d/ingest "PROV1" (du/umm-spec-collection {:entry-title "et2"
                                                           :version-id "v2"
                                                           :short-name "s2"}))
        concept2 {:provider-id "PROV1"
                  :concept-type :collection
                  :native-id (:entry-title coll2-2)}
        coll2-3-tombstone (merge coll2-2 {:deleted true} (ingest/delete-concept concept2))

        coll3 (d/ingest "PROV2" (du/umm-spec-collection {:entry-title "et3"
                                                         :version-id "v4"
                                                         :short-name "s1"})
                        {:user-id "user3"})]
    (index/wait-until-indexed)
    (testing "find collections in legacy UMM JSON format"
      (are3 [url-extension collections params]
        (du/assert-legacy-umm-jsons-match
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

    (testing "finding collections in different versions of UMM JSON"
      (are3 [version accept-header url-extension]
        (let [options (if accept-header
                        {:accept accept-header}
                        {:url-extension url-extension})
              collections [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
              response (search/find-concepts-umm-json :collection {:all-revisions true} options)]
          (du/assert-umm-jsons-match version collections response))
        "Latest version is default with accept header of UMM JSON Search Results"
        umm-version/current-collection-version mt/umm-json-results nil

        "Latest version is default with accept header of UMM JSON"
        umm-version/current-collection-version mt/umm-json nil

        "Retrieve older version with accept header of UMM JSON Search Results"
        "1.3" (str mt/umm-json-results ";version=1.3") nil
        "Retrieve older version with accept header of UMM JSON"
        "1.3" (str mt/umm-json ";version=1.3") nil

        "Retrieve specified version 1.4 with URL extension"
        "1.4" nil "umm_json_v1_4"
        "Retrieve specified version 1.3 with URL extension"
        "1.3" nil "umm_json_v1_3"
        "Retrieve specified version 1.0 with URL extension"
        "1.0" nil "umm_json_v1_0"

        "Retrieve specified version 1.16.1 with URL extension"
        "1.16.1" nil "umm_json_v1_16_1"))

    (testing "find collections in umm-json format"
      (are3 [collections params]
        (du/assert-umm-jsons-match
         umm-version/current-collection-version collections
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
        {:concept-id "C1200000009-PROV1" :all-revisions false}

        "concept-id all-revisions unspecified"
        [coll1-3]
        {:concept-id "C1200000009-PROV1"}

        "concept-id all-revisions true"
        [coll1-1 coll1-2-tombstone coll1-3]
        {:concept-id "C1200000009-PROV1" :all-revisions true}

        "all-revisions true"
        [coll1-1 coll1-2-tombstone coll1-3 coll2-1 coll2-2 coll2-3-tombstone coll3]
        {:all-revisions true}))))

(deftest search-umm-json-error-cases
  (testing "Searching with invalid UMM JSON extension"
    (is (= {:status 400
            ;; XML is returned here because we don't specify an accept header and the URL extension is unknown.
            :errors "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>The URL extension [umm_json_v1_A] is not supported.</error></errors>"}
           (select-keys (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v1_A"}) [:status :errors])))
    (is (= {:status 400
            ;; XML is returned here because we don't specify an accept header and the URL extension is unknown.
            :errors "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>The URL extension [umm_json_v1_4_A] is not supported.</error></errors>"}
           (select-keys (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v1_4_A"}) [:status :errors])))
    (is (= {:status 400
            ;; XML is returned here because we don't specify an accept header and the URL extension is unknown.
            :errors "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>The URL extension [umm_json_v1.4.5] is not supported.</error></errors>"}
           (select-keys (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v1.4.5"}) [:status :errors]))))

  (testing "Searching with older non-existent UMM JSON version"
    (is (= {:status 400
            :errors ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [0.1] is not supported for collections."]}
           (dissoc (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v0_1"}) :body)))
    (is (= {:status 400
            :errors ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [0.1] is not supported for collections."]}
           (dissoc (search/find-concepts-umm-json
                    :collection {}
                    {:accept (mt/with-version mt/umm-json "0.1")})
                   :body))))
  (testing "Searching with future UMM JSON version"
    (is (= {:status 400
            :errors ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [9.1] is not supported for collections."]}
           (dissoc (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v9_1"}) :body))))

  (testing "Searching with non existing UMM JSON version using major, minor, macro version"
    (is (= {:status 400
            :errors ["The mime type [application/vnd.nasa.cmr.umm_results+json] with version [1.14.3] is not supported for collections."]}
           (dissoc (search/find-concepts-umm-json :collection {} {:url-extension "umm_json_v1_14_3"}) :body)))))
