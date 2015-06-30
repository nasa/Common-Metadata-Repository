(ns cmr.system-int-test.search.collection-concept-revision-search-test
  "Integration test for collection concept map search with params"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :refer [are2]]
            [cmr.transmit.config :as transmit-config]
            [cmr.common.mime-types :as mt]
            [cmr.common.time-keeper :as tk]
            [clj-time.format :as f]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- umm->concept-map
  "Convert a UMM collection record into concept map like the ones returned by the find-concepts API"
  [umm]
  (let [{:keys [entry-id entry-title revision-id deleted?
                provider-id concept-id product format-key]} umm
        {:keys [short-name version-id]} product]
    {:provider-id provider-id
     :revision-id revision-id
     :native-id entry-title
     ;; Need to convert nils to explicit false
     :deleted (if deleted? true false)
     :format (mt/format->mime-type format-key)
     :concept-id concept-id
     :concept-type "collection"
     :extra-fields {:entry-title entry-title
                    :entry-id entry-id
                    :short-name short-name
                    :version-id version-id}}))

(defn- search-results-match
  "Compare the expected results to the actual results of a parameter search"
  [umms search-response]
  (let [result-set (set (map (fn [result]
                               (-> result
                                   (dissoc :revision-date)
                                   (update-in [:extra-fields] dissoc :delete-time)))
                             (:body search-response)))
        umm-concept-map-set (do (set (map umm->concept-map umms)))]
    (= umm-concept-map-set result-set)))

(deftest retrieve-collection-concept-revisions-by-params

  (let [umm-coll (dc/collection {:entry-title "et1"
                                 :entry-id "s1_v1"
                                 :version-id "v1"
                                 :short-name "s1"})
        ;; ingest twice to get different revision-id
        _ (d/ingest "PROV1" umm-coll)
        coll1 (d/ingest "PROV1" umm-coll)

        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "et2"
                                                :entry-id "s2_v2"
                                                :version-id "v2"
                                                :short-name "s2"}))
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "et3"
                                                :version-id "v3"
                                                :short-name "s1"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "et4"
                                                :version-id "v3"
                                                :short-name "s4"}))]
    (index/wait-until-indexed)

    (testing "find-with-parameters"
      (testing "latest revisions"
        (are2 [collections params]
              (search-results-match collections (search/find-concept-revisions
                                                  :collection
                                                  (assoc params :latest true)
                                                  {:headers {transmit-config/token-header
                                                             (transmit-config/echo-system-token)
                                                             "Accept" "application/json"}}))
              "provider-id"
              [coll1 coll2] {:provider-id "PROV1" :exclude-metadata true}

              "provider-id, entry-title"
              [coll1] {:provider-id "PROV1" :entry-title "et1" :exclude-metadata true}

              "provider-id, entry-id"
              [coll2] {:provider-id "PROV1" :entry-id "s2_v2" :exclude-metadata true}

              "short-name, version-id"
              [coll2] {:short-name "s2" :version-id "v2" :exclude-metadata true}

              "mixed providers - short-name"
              [coll1 coll3] {:short-name "s1" :exclude-metadata true}

              "find none - bad provider-id"
              [] {:provider-id "PROV_NONE" :exclude-metadata true}

              "find none - provider-id, bad version-id"
              [] {:provider-id "PROV1" :version-id "v7" :exclude-metadata true}))
      #_(testing "all revisions"
          (are2 [rev-count params]
                (= rev-count
                   (count (:body (search/find-concept-revisions
                                   :collection params {:headers {transmit-config/token-header
                                                                 (transmit-config/echo-system-token)
                                                                 "Accept" "application/json"}}))))
                "provider-id - three revisions"
                3 {:provider-id "PROV1"}

                "entry-title - two revisons"
                2 {:entry-title "et1"}))
      #_(testing "ACLs"
          ;; no token
          (is (= 401
                 (:status (search/find-concept-revisions :collection {:provider-id "PROV1"}))))))))

