(ns cmr.system-int-test.search.collection-concept-revision-search-test
  "Integration test for collection concept map search with params"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]
            [cmr.common.util :refer [are2]]
            [cmr.transmit.config :as transmit-config]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(defn- umm->concept-map
  "Convert a UMM collection record into concept map like the ones returned by the find-concepts API"
  [umm deleted?]
  (let [{:keys [entry-id entry-title revision-id provider-id concept-id product]} umm
        {:keys [short-name version-id]} product]
    {:provider-id provider-id
     :revision-id revision-id
     :deleted deleted?
     :concept-id concept-id
     :concept-type "collection"
     :extra-fields {:entry-title entry-title
                    :entry-id entry-id
                    :short-name short-name
                    :version-id version-id}}))

(defn- strip-unneeded-fields
  "Remove fields from a concept map not needed for comparing to a UMM record"
  [con-map]
  (-> (dissoc con-map :format :revision-date :native-id)
      (update-in [:extra-fields] dissoc :delete-time)))


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
              (= (set (map #(umm->concept-map % false) collections))
                 (set (map strip-unneeded-fields (:body (search/find-concept-revisions
                                                          :collection
                                                          (assoc params :latest true)
                                                          (transmit-config/echo-system-token))))))
              "provider-id"
              [coll1 coll2] {:provider-id "PROV1"}

              "provider-id, entry-title"
              [coll1] {:provider-id "PROV1" :entry-title "et1"}


              "provider-id, entry-id"
              [coll2] {:provider-id "PROV1" :entry-id "s2_v2"}


              "short-name, version-id"
              [coll2] {:short-name "s2" :version-id "v2"}

              "mixed providers - short-name"
              [coll1 coll3] {:short-name "s1"}

              "find none - bad provider-id"
              [] {:provider-id "PROV_NONE"}

              "find none - provider-id, bad version-id"
              [] {:provider-id "PROV1" :version-id "v7"}))
      (testing "all revisions"
        (are2 [rev-count params]
              (= rev-count
                 (count (:body (search/find-concept-revisions
                                 :collection params (transmit-config/echo-system-token)))))
              "provider-id - three revisions"
              3 {:provider-id "PROV1"}

              "entry-title - two revisons"
              2 {:entry-title "et1"}))
      (testing "ACLs"
        ;; no token
        (is (= 401
               (:status (search/find-concept-revisions :collection {:provider-id "PROV1"}))))))))

