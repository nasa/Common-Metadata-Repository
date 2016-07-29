(ns cmr.system-int-test.search.granule-search-test
  "Integration test for CMR granule search"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.common.services.messages :as msg]
            [cmr.search.services.messages.common-messages :as smsg]
            [cmr.common-app.services.search.messages :as cmsg]
            [cmr.common-app.services.search.messages :as vmsg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "CMR_T_PROV"}))

(comment
  (dev-sys-util/reset)
  (doseq [p ["PROV1" "PROV2" "CMR_T_PROV"]]
    (ingest/create-provider {:provider-guid (str "guid-" p) :provider-id p}))

  (def coll1 (d/ingest "PROV1" (dc/collection {})))
  (def gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))))

(deftest search-by-provider-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "Granule5"}))]
    (index/wait-until-indexed)
    (testing "search by non-existent provider id."
      (is (d/refs-match?
            []
            (search/find-refs :granule {:provider "NON_EXISTENT"}))))
    (testing "search by existing provider id."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "PROV1"}))))
    (testing "search by provider id using wildcard *."
      (is (d/refs-match?
            [gran1 gran2 gran3 gran4 gran5]
            (search/find-refs :granule {:provider "PRO*"
                                        "options[provider][pattern]" "true"}))))
    (testing "search by provider id using wildcard ?."
      (is (d/refs-match?
            [gran1 gran2 gran3 gran4 gran5]
            (search/find-refs :granule {:provider "PROV?"
                                        "options[provider][pattern]" "true"}))))
    (testing "search by provider id defaut is ignroe case true."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "PROV1"}))))
    (testing "search by provider id ignore case false"
      (is (d/refs-match?
            []
            (search/find-refs :granule {:provider "prov1"
                                        "options[provider][ignore-case]" "false"}))))
    (testing "search by provider id ignore case true."
      (is (d/refs-match?
            [gran1 gran2 gran3]
            (search/find-refs :granule {:provider "prov1"
                                        "options[provider][ignore-case]" "true"}))))
    (testing "aql search"
      (is (d/refs-match? [gran1 gran2 gran3]
                         (search/find-refs-with-aql :granule [] {:dataCenterId "PROV1"}))))))

(deftest search-by-dataset-id
  (let [coll1 (d/ingest "PROV1" (dc/collection {:entry-title "OneCollectionV1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-title "AnotherCollectionV1"}))
        coll3 (d/ingest "PROV2" (dc/collection {:entry-title "OneCollectionV1"}))
        coll4 (d/ingest "PROV2" (dc/collection {:entry-title "OtherCollectionV1"}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll2 {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule coll3 {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule coll4 {:granule-ur "Granule5"}))]
    (index/wait-until-indexed)

    (testing "search granule by dataset id."
      (are [items ids options]
           (let [params (merge {:dataset-id ids}
                               (when options
                                 {"options[dataset-id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [] "NON_EXISTENT" {}
           [gran3] "AnotherCollectionV1" {}
           [gran3 gran5] ["AnotherCollectionV1", "OtherCollectionV1"] {}
           ;search across different providers
           [gran1 gran2 gran4] "OneCollectionV1" {}

           ;; pattern
           [gran1 gran2 gran4 gran5] "O*" {:pattern true}
           [gran5] "OtherCollectionV?" {:pattern true}

           ;; ignore case
           [] "anotherCollectionV1" {:ignore-case false}
           [gran3] "anotherCollectionV1" {:ignore-case true}
           [gran3] "anotherCollectionV1" {}))

    (testing "search by existing dataset id, verify result."
      (let [{:keys [refs]} (search/find-refs :granule {:dataset-id "AnotherCollectionV1"})]
        (is (= 1 (count refs)))
        (let [ref (first refs)
              {:keys [name id location]} ref]
          (is (= "Granule3" name))
          (is (re-matches #"G[0-9]+-PROV1" id)))))

    (testing "search granule by dataset id with aql"
      (are [items ids options]
           (let [condition (merge {:dataSetId ids} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [] "NON_EXISTENT" {}
           [gran3] "AnotherCollectionV1" {}
           [gran3 gran5] ["AnotherCollectionV1", "OtherCollectionV1"] {}
           ;search across different providers
           [gran1 gran2 gran4] "OneCollectionV1" {}

           ;; pattern
           [gran1 gran2 gran4 gran5] "O%" {:pattern true}
           [gran5] "OtherCollectionV_" {:pattern true}

           ;; ignore case
           [] "anotherCollectionV1" {:ignore-case false}
           [gran3] "anotherCollectionV1" {:ignore-case true}
           [] "anotherCollectionV1" {}))))


(def provider-granules
  {"PROV1" [{:entry-title "OneCollectionV1"
             :granule-ur "Granule1"}
            {:entry-title "OneCollectionV1"
             :granule-ur "Granule2"}
            {:entry-title "AnotherCollectionV1"
             :granule-ur "Granule3"}]

   "PROV2" [{:entry-title "OneCollectionV1"
             :granule-ur "Granule4"}
            {:entry-title "OtherCollectionV1"
             :granule-ur "Granule5"}]

   "CMR_T_PROV" [{:entry-title "TestCollection"
                  :granule-ur "Granule4"}
                 {:entry-title "TestCollection"
                  :granule-ur "SampleUR1"}
                 {:entry-title "TestCollection"
                  :granule-ur "SampleUR2"}
                 {:entry-title "TestCollection"
                  :granule-ur "sampleur3"}]})

(deftest search-by-granule-ur
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "Granule3"}))
        gran5 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "SampleUR1"}))
        gran6 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "SampleUR2"}))
        gran7 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "sampleur33"}))]
    (index/wait-until-indexed)

    (testing "search granule by granule ur."
      (are [items urs options]
           (let [params (merge {:granule-ur urs}
                               (when options
                                 {"options[granule-ur]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [] "NON_EXISTENT" {}
           [gran1] "Granule1" {}
           [gran1 gran2] ["Granule1", "Granule2"] {}
           ;search across different providers
           [gran3 gran4] "Granule3" {}

           ;; pattern
           [gran5 gran6 gran7] "S*" {:pattern true}
           [gran5 gran6] "SampleUR?" {:pattern true}

           ;; ignore case
           [] "sampleUR1" {:ignore-case false}
           [gran5] "sampleUR1" {:ignore-case true}
           [gran5] "sampleUR1" {}
           [gran5 gran6] "sampleUR?" {:ignore-case true :pattern true}
           [gran5 gran6] "sampleUR?" {:pattern true}))

    (testing "search granule by granule ur with aql"
      (are [items urs options]
           (let [condition (merge {:GranuleUR urs} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [] "NON_EXISTENT" {}
           [gran1] "Granule1" {}
           [gran1 gran2] ["Granule1", "Granule2"] {}
           ;search across different providers
           [gran3 gran4] "Granule3" {}

           ;; pattern
           [gran5 gran6] "S%" {:pattern true}
           [gran5 gran6] "SampleUR_" {:pattern true}

           ;; ignore case
           [] "sampleUR1" {:ignore-case false}
           [gran5] "sampleUR1" {:ignore-case true}
           [] "sampleUR1" {}
           [gran5 gran6] "sampleUR_" {:ignore-case true :pattern true}
           [] "sampleUR_" {:pattern true}))))

(defn- make-catalog-rest-style-query
  "Make a cloud-cover query in the catalog-reset style."
  [min max]
  (cond
    (and min max)
    {"cloud-cover[min]" min "cloud-cover[max]" max}

    min
    {"cloud-cover[min]" min}

    max
    {"cloud-cover[max]" max}

    :else
    (throw (Exception. "Tests must specify either max or min cloud-cover."))))


(deftest search-by-cloud-cover
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 0.8}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 30.0}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 120}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:cloud-cover -60.0}))
        gran5 (d/ingest "PROV2" (dg/granule coll2 {:cloud-cover 0.0}))
        gran6 (d/ingest "PROV2" (dg/granule coll2 {:granule-ur "sampleur3"}))]
    (index/wait-until-indexed)
    (testing "search granules with valid cloud-cover value"
      (are [cloud-cover items]
           (d/refs-match? items (search/find-refs :granule {"cloud_cover" cloud-cover}))

           "0.2," [gran1 gran2 gran3]
           ",0.7" [gran4 gran5]
           "-70.0,31.0" [gran1 gran2 gran4 gran5]
           "-70.0,120.0" [gran1 gran2 gran3 gran4 gran5]
           ;; Empty cloud cover is allowed.
           ;; It is as if no cloud cover parameter is present and will find everything.
           "" [gran1 gran2 gran3 gran4 gran5 gran6]))

    (testing "search by cloud-cover with min value greater than max value"
      (let [min-value 30.0
            max-value 0.0]
        (is (= {:status 400
                :errors [(vmsg/min-value-greater-than-max min-value max-value)]}
               (search/find-refs :granule {"cloud_cover" (format "%s,%s" min-value max-value)})))))
    (testing "search by cloud-cover with non numeric str 'c9c,'"
      (let [num-range "c9c,"]
        (is (= {:status 400
                :errors [(msg/invalid-msg java.lang.Double "c9c")]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with non numeric str ',99c'"
      (let [num-range ",99c"]
        (is (= {:status 400
                :errors [(msg/invalid-msg java.lang.Double "99c")]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with non numeric str ','"
      (let [num-range ","]
        (is (= {:status 400
                :errors [(msg/invalid-numeric-range-msg num-range)]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "search by cloud-cover with invalid range"
      (let [num-range "30,c9c"]
        (is (= {:status 400
                :errors [(msg/invalid-msg java.lang.Double "c9c")]}
               (search/find-refs :granule {"cloud_cover" num-range})))))
    (testing "catalog-rest style"
      (are [min max items]
           (d/refs-match? items (search/find-refs :granule (make-catalog-rest-style-query min max)))
           0.2 nil [gran1 gran2 gran3]
           nil 0.7 [gran4 gran5]
           -70.0 31.0 [gran1 gran2 gran4 gran5]))

    (testing "cloud cover granule search with aql"
      (are [items cloud-cover]
           (d/refs-match? items
                          (search/find-refs-with-aql :granule
                                                     [{:cloudCover cloud-cover}]))

           [gran1 gran2 gran3] [0.2 nil]
           [gran4 gran5] [nil 0.7]
           [gran1 gran2 gran4 gran5] [-70.0 31.0]))))

;; exclude granules by echo_granule_id or concept_id (including parent concept_id) params
(deftest exclude-granules-by-echo-granule-n-concept-ids
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV2" (dc/collection {}))
        gran1 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 0.8}))
        gran2 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 30.0}))
        gran3 (d/ingest "PROV1" (dg/granule coll1 {:cloud-cover 120}))
        gran4 (d/ingest "PROV2" (dg/granule coll2 {:cloud-cover -60.0}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1-cid (get-in gran1 [:concept-id])
        gran2-cid (get-in gran2 [:concept-id])
        gran3-cid (get-in gran3 [:concept-id])
        gran4-cid (get-in gran4 [:concept-id])]
    (index/wait-until-indexed)
    (testing "fetch all granules with cloud-cover attrib"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {"cloud_cover" "-70,120"} [gran1 gran2 gran3 gran4]))
    (testing "fetch all granules with cloud-cover attrib to exclude a single granule from the set"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {:exclude {:echo_granule_id [gran1-cid]}, :cloud_cover "-70,120"} [gran2 gran3 gran4]))
    (testing "fetch all granules with cloud-cover attrib to exclude multiple granules from the set"
      (are [cc-search items] (d/refs-match? items (search/find-refs :granule cc-search))
           {:exclude {:echo_granule_id [gran1-cid gran2-cid]}, :cloud_cover "-70,120"} [gran3 gran4]))
    (testing "fetch granules by echo granule ids to exclude multiple granules from the set"
      (are [srch-params items] (d/refs-match? items (search/find-refs :granule srch-params))
           {:exclude {:echo_granule_id [gran1-cid gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]} [gran3]))
    (testing "fetch granules by echo granule ids to exclude multiple granules from the set by concept_id"
      (are [srch-params items] (d/refs-match? items (search/find-refs :granule srch-params))
           {:exclude {:concept_id [gran1-cid gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]} [gran3]))
    (testing "fetch granules by echo granule ids to exclude a granule by invalid exclude param - dataset_id"
      ;; dataset-id aliases to entry-title - there is no easy way to recover original search param on error
      (let [srch1 {:exclude {:dataset-id [gran2-cid]}, :echo_granule_id [gran1-cid gran2-cid gran3-cid]}
            srch2 {:exclude {:dataset-id [gran2-cid] :concept-id [gran1-cid]}, :echo_granule_id [gran1-cid gran2-cid]}]
        (is (= {:status 400
                :errors [(smsg/invalid-exclude-param-msg #{:entry-title})]}
               (search/find-refs :granule srch1)))
        (is (= {:status 400
                :errors [(smsg/invalid-exclude-param-msg #{:entry-title})]}
               (search/find-refs :granule srch2)))))))

;; Find granules by echo_granule_id, echo_collection_id and concept_id params
(deftest search-by-concept-id
  (let [coll1 (d/ingest "PROV1" (dc/collection))
        coll2 (d/ingest "PROV2" (dc/collection))
        gran1 (d/ingest "PROV1" (dg/granule coll1))
        gran2 (d/ingest "PROV1" (dg/granule coll1))
        gran3 (d/ingest "PROV1" (dg/granule coll1))
        gran4 (d/ingest "PROV2" (dg/granule coll2))
        gran5 (d/ingest "PROV2" (dg/granule coll2))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1-cid (get-in gran1 [:concept-id])
        gran2-cid (get-in gran2 [:concept-id])
        gran3-cid (get-in gran3 [:concept-id])
        gran4-cid (get-in gran4 [:concept-id])
        gran5-cid (get-in gran5 [:concept-id])]
    (index/wait-until-indexed)
    (testing "echo granule id search"
      (are [items cid options]
           (let [params (merge {:echo_granule_id cid}
                               (when options
                                 {"options[echo_granule_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}))

    (testing "search granule by echo granule id with aql"
      (are [items ids options]
           (let [condition (merge {:ECHOGranuleID ids} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}))

    (testing "echo granule id search - disallow ignore case"
      (is (= {:status 400
              :errors [(cmsg/invalid-opt-for-param :concept-id :ignore-case)]}
             (search/find-refs :granule {:echo_granule_id gran1-cid "options[echo_granule_id]" {:ignore_case true}}))))
    (testing "Search with wildcards in echo_granule_id param not supported."
      (is (= {:status 400
              :errors [(cmsg/invalid-opt-for-param :concept-id :pattern)]}
             (search/find-refs :granule {:echo_granule_id "G*" "options[echo_granule_id]" {:pattern true}}))))
    (testing "search granules by echo collection id"
      (are [items cid options]
           (let [params (merge {:echo_collection_id cid}
                               (when options
                                 {"options[echo_collection_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran3] coll1-cid {}
           [gran4 gran5] coll2-cid {}))

    (testing "search granule by echo collection id with aql"
      (are [items ids options]
           (let [condition (merge {:ECHOCollectionID ids} options)]
             (d/refs-match? items (search/find-refs-with-aql :granule [condition])))

           [gran1 gran2 gran3] coll1-cid {}
           [gran4 gran5] coll2-cid {}))

    (testing "search granules by parent concept id"
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))

           [gran1 gran2 gran3] coll1-cid {}
           [gran4 gran5] coll2-cid {}))
    (testing "search granules by concept id"
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/refs-match? items (search/find-refs :granule params)))
           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}
           [] [gran1-cid gran5-cid] {:and true}))
    (testing "search granules by concept id retrieve metadata"
      ;; This type of query skips elastic and goes straight to transformer,
      ;; so we need this particular configuration to test this.
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/assert-metadata-results-match :echo10
                                              items
                                              (search/find-metadata :granule :echo10 params)))
           [gran1] gran1-cid {}
           [gran5] gran5-cid {}
           ;; Multiple values
           [gran1 gran2 gran3 gran4 gran5] [gran1-cid gran2-cid gran3-cid gran4-cid gran5-cid] {}
           ;; an non existent granule along with existing granules
           [gran1 gran5] [gran1-cid "G555-PROV1" "G555-NON_EXIST" gran5-cid] {}))
    (testing "Search with wildcards in concept_id param not supported."
      (is (= {:status 400
              :errors [(cmsg/invalid-opt-for-param :concept-id :pattern)]}
             (search/find-refs :granule {:concept_id "G*" "options[concept_id]" {:pattern true}}))))
    (testing "OR option is not supported for anything but attribute, science-keywords"
      (is (= {:status 400
              :errors [(cmsg/invalid-opt-for-param :concept-id :or)]}
             (search/find-refs :granule {:concept-id "G" "options[concept_id]" {:or true}}))))
    (testing "Mixed arity param results in 400 error"
      (is (= {:status 400
              :errors [(smsg/mixed-arity-parameter-msg :concept-id)]}
             (search/make-raw-search-query :granule ".json?concept_id=G&concept_id[pattern]=true"))))))
