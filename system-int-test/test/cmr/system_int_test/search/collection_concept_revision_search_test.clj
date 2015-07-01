(ns cmr.system-int-test.search.collection-concept-revision-search-test
  "Integration test for collection concept map search with params"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :refer [are2] :as util]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.mime-types :as mt]
            [cmr.umm.core :as umm]
            [clj-time.format :as f]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- umm->concept-map
  "Convert a UMM collection record into concept map like the ones returned by the find-concepts API"
  [umm]
  (let [{:keys [entry-id entry-title revision-id metadata
                provider-id concept-id product format-key]} umm
        deleted? (:deleted umm)
        {:keys [short-name version-id]} product
        concept-map {:provider-id provider-id
                     :revision-id revision-id
                     :native-id entry-title
                     :metadata metadata
                     ;; Need to convert nils to explicit false
                     :deleted (if deleted? true false)
                     :format (mt/format->mime-type format-key)
                     :concept-id concept-id
                     :concept-type "collection"
                     :extra-fields {:entry-title entry-title
                                    :entry-id entry-id
                                    :short-name short-name
                                    :version-id version-id}}]
    (util/remove-nil-keys concept-map)))

(defn- search-results-match
  "Compare the expected results to the actual results of a parameter search"
  [umms search-response]
  (let [result-set (set (map (fn [result]
                               (-> result
                                   (dissoc :revision-date)
                                   (update-in [:extra-fields] dissoc :delete-time)))
                             (:body search-response)))
        umm-concept-map-set (do (set (map umm->concept-map umms)))]
    ;; Added the 'is' wrapper so I can see the respective sets + difference when a test fails.
    (is (= umm-concept-map-set result-set))))

(deftest retrieve-collection-concept-revisions-by-params

  (let [umm-coll1 (dc/collection {:entry-title "et1"
                                  :entry-id "s1_v1"
                                  :version-id "v1"
                                  :short-name "s1"})

        umm-coll2 (dc/collection {:entry-title "et2"
                                  :entry-id "s2_v2"
                                  :version-id "v2"
                                  :short-name "s2"})

        ;; Ingest collection twice and then tombstone - latest should be deleted=true.
        coll1-1 (d/ingest "PROV1" umm-coll1)

        coll1-2 (d/ingest "PROV1" umm-coll1)

        coll1-tombstone {:concept-id (:concept-id coll1-2)
                         :revision-id (inc (:revision-id coll1-2))}

        _ (ingest/tombstone-concept coll1-tombstone)

        coll1-tombstone (merge coll1-2 {:deleted true :revision-id (inc (:revision-id coll1-2))})

        ;; Ingest collection once, tombstone, then ingest again - latest should be deleted=false.
        coll2-1 (d/ingest "PROV1" umm-coll2)

        coll2-tombstone {:concept-id (:concept-id coll2-1)
                         :revision-id (inc (:revision-id coll2-1))}

        _ (ingest/tombstone-concept coll2-tombstone)

        coll2-tombstone (merge coll2-1 {:deleted true :revision-id (inc (:revision-id coll2-1))})

        coll2-3 (d/ingest "PROV1" umm-coll2)

        ;; Ingest a couple of collections once each.
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "et3"
                                                :version-id "v3"
                                                :short-name "s1"}))

        coll3+metadata (assoc coll3 :metadata (umm/umm->xml coll3 :echo10))

        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "et1"
                                                :version-id "v3"
                                                :short-name "s4"}))]
    (index/wait-until-indexed)

    (testing "find-with-parameters"
      (testing "various parameter combinations"
        (are2 [collections params]
              (search-results-match collections (search/find-concept-revisions
                                                  :collection
                                                  params
                                                  {:headers {transmit-config/token-header
                                                             (transmit-config/echo-system-token)
                                                             "Accept" "application/json"}}))
              "provider-id - latest=true"
              [coll1-tombstone coll2-3]
              {:provider-id "PROV1" :exclude-metadata true :latest true}

              "provider-id - latest=false"
              [coll1-1 coll1-2 coll1-tombstone coll2-1 coll2-tombstone coll2-3]
              {:provider-id "PROV1" :exclude-metadata true :latest false}

              "provider-id - latest unspecified"
              [coll1-1 coll1-2 coll1-tombstone coll2-1 coll2-tombstone coll2-3]
              {:provider-id "PROV1" :exclude-metadata true}

              "provider-id, entry-title - latest=true"
              [coll1-tombstone]
              {:provider-id "PROV1" :entry-title "et1" :exclude-metadata true :latest true}

              "provider-id, entry-id - latest=true"
              [coll2-3]
              {:provider-id "PROV1" :entry-id "s2_v2" :exclude-metadata true :latest true}

              "short-name, version-id - latest"
              [coll2-3]
              {:short-name "s2" :version-id "v2" :exclude-metadata true :latest true}

              "mixed providers - short-name - latest=true"
              [coll1-tombstone coll3]
              {:short-name "s1" :exclude-metadata true :latest true}

              "mixed providers - entry-title - latest=false"
              [coll1-1 coll1-2 coll1-tombstone coll4]
              {:entry-title "et1" :exclude-metadata true :latest false}

              "version-id- latest=true"
              [coll3 coll4]
              {:version-id "v3" :exclude-metadata true :latest true}

              "entry-title - exclude_metadata=false"
              [coll3+metadata]
              {:entry-title "et3" :exclude-metadata false :latest true}

              "entry-title - exclude_metadata unspecified"
              [coll3+metadata]
              {:entry-title "et3" :latest true}

              "find none - bad provider-id"
              []
              {:provider-id "PROV_NONE" :exclude-metadata true :latest true}

              "find none - provider-id, bad version-id"
              []
              {:provider-id "PROV1" :version-id "v7" :exclude-metadata true :latest true}))

      (testing "granule finds are not supported"
        (is (= 400
               (:status (search/find-concept-revisions :granule
                                                       {:provider-id "PROV1"}
                                                       {:headers {transmit-config/token-header
                                                                  (transmit-config/echo-system-token)
                                                                  "Accept" "application/json"}})))))
      (testing "ACLs"
        ;; no token - This is temporary and will be updated in issue CMR-1771.
        (is (= 401
               (:status (search/find-concept-revisions :collection {:provider-id "PROV1"}))))))))

