(ns cmr.system-int-test.search.granule-search-test
  "Integration test for CMR granule search"
  (:require
   [clojure.string :as s]
   [clojure.test :refer :all]
   [cmr.common-app.services.search.messages :as cmsg]
   [cmr.common-app.services.search.messages :as vmsg]
   [cmr.common.services.messages :as msg]
   [cmr.common.util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as e]
   [cmr.search.services.messages.common-messages :as smsg]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.search.granule-spatial-search-test :as st]
   [cmr.system-int-test.system :as int-s]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2" "provguid3" "CMR_T_PROV"}))

(comment
  (dev-sys-util/reset)
  (doseq [p ["PROV1" "PROV2" "CMR_T_PROV"]]
    (ingest/create-provider {:provider-guid (str "guid-" p) :provider-id p}))

  (def coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {})))
  (def gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 "C1-PROV1" {:granule-ur "Granule1"}))))

(deftest search-by-native-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule5"}))]
    (index/wait-until-indexed)

    (are3 [items search]
          (is (d/refs-match? items (search/find-refs :granule search)))

          "search by non-existent native id."
          [] {:native-id "NON_EXISTENT"}

          "search by existing native id."
          [gran1] {:native-id "Granule1"}

          "search by native-id using wildcard *."
          [gran1 gran2 gran3 gran4 gran5] {:native-id "Gran*" "options[native-id][pattern]" "true"}

          "search by native-id using wildcard ?."
          [gran1 gran2 gran3 gran4 gran5] {:native-id "Granule?" "options[native-id][pattern]" "true"}

          "search by native-id defaut is ignore case true."
          [gran1] {:native-id "granule1"}

          "search by native-id ignore case false"
          [] {:native-id "granule1" "options[native-id][ignore-case]" "false"}

          "search by native-id ignore case true."
          [gran1] {:native-id "granule1" "options[native-id][ignore-case]" "true"})))

(deftest search-by-provider-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule5"}))]
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "OneCollectionV1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "AnotherCollectionV1"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll3 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "OneCollectionV1"
                                                                            :ShortName "S3"
                                                                            :Version "V3"}))
        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "OtherCollectionV1"
                                                                            :ShortName "S4"
                                                                            :Version "V4"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        coll3-cid (get-in coll3 [:concept-id])
        coll4-cid (get-in coll4 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll3 coll3-cid {:granule-ur "Granule4"}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll4 coll4-cid {:granule-ur "Granule5"}))]
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule1"}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule2"}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:granule-ur "Granule3"}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "Granule3"}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "SampleUR1"}))
        gran6 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "SampleUR2"}))
        gran7 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "sampleur33"}))]
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 0.8}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 30.0}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 120}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:cloud-cover -60.0}))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:cloud-cover 0.0}))
        gran6 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:granule-ur "sampleur3"}))]
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 0.8}))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 30.0}))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid {:cloud-cover 120}))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid {:cloud-cover -60.0}))
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "E1"
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:EntryTitle "E2"
                                                                            :ShortName "S2"
                                                                            :Version "V2"}))
        coll1-cid (get-in coll1 [:concept-id])
        coll2-cid (get-in coll2 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid))
        gran2 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid))
        gran3 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid))
        gran4 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid))
        gran5 (d/ingest "PROV2" (dg/granule-with-umm-spec-collection coll2 coll2-cid))
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
      (is (= {:errors
              ["Concept-id [G*] is not valid."
               "Option [pattern] is not supported for param [concept_id]"],
              :status 400}
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
      (is (= {:errors
              ["Concept-id [G*] is not valid."
               "Option [pattern] is not supported for param [concept_id]"],
              :status 400}
             (search/find-refs :granule {:concept_id "G*" "options[concept_id]" {:pattern true}}))))
    (testing "OR option is not supported for anything but attribute, science-keywords"
      (is (= {:errors
              ["Concept-id [G] is not valid."
               "Option [or] is not supported for param [concept_id]"],
              :status 400}
             (search/find-refs :granule {:concept-id "G" "options[concept_id]" {:or true}}))))
    (testing "Mixed arity param results in 400 error"
      (is (= {:status 400
              :errors [(smsg/mixed-arity-parameter-msg :concept-id)]}
             (search/make-raw-search-query :granule ".json?concept_id=G&concept_id[pattern]=true"))))))

(deftest block-excessive-queries-test
  (testing "Blocking those MCD43A4 queries"
    (is (= {:status 429
            :errors ["Excessive query rate. Please contact cmr-support@earthdata.nasa.gov."]}
           (search/make-raw-search-query :granule ".json?short_name=MCD43A4&&page_size=5")))))

(deftest entry-title-with-preceeding-succeeding-whitespace-test
  (e/ungrant (int-s/context) "ACL1200000001-CMR")
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle " E1 "
                                                                            :ShortName "S1"
                                                                            :Version "V1"}))
        coll1-cid (get-in coll1 [:concept-id])
        gran1 (d/ingest "PROV1" (dg/granule-with-umm-spec-collection coll1 coll1-cid))]
    (e/grant-guest (int-s/context) (e/gran-catalog-item-id "PROV1" (e/coll-id [" E1 "])))
    (index/wait-until-indexed)
    (d/refs-match? [gran1] (search/find-refs :granule (merge {:concept_id coll1-cid})))))

(deftest granule-search-with-multiple-spatial-representations-testing
  (let [coll (d/ingest-concept-with-metadata-file "CMR-8244/C2075141605-POCLOUD.echo10"
                                                  {:provider-id "PROV1"
                                                   :concept-type :collection
                                                   :native-id "C2-collection"
                                                   :format-key :echo10})
        _ (index/wait-until-indexed)
        granule (d/ingest-concept-with-metadata-file "CMR-8244/G2363859382-POCLOUD.echo10"
                                                     {:provider-id "PROV1"
                                                      :concept-type :granule
                                                      :concept-id "G23-PROV1"
                                                      :native-id "G23-granule"
                                                      :format-key :echo10})
        intersects-none [29.8125 19.0954 36.5625 12.90915 53.15625 23.0321 40.5 35.6858 29.8125 19.0954]
        intersects-bbox [-110.8125 -7.61796 -104.34375 -19.42808 -86.90625 -21.11524 -84.09375 -5.9308 -110.8125 -7.61796]
        intersects-polygon [99 39.30683 67.5 33.12058 76.5 11.74989 104.625 21.31046 99 39.30683]
        intersects-both [-150.75 -36.61535 162.5625 -29.30433 176.0625 -84.9806 -145.6875 -89.47969 -150.75 -36.61535]
        _ (index/wait-until-indexed)]

    (testing "Polygon search with 'any' flag"
      (comment)
      (testing "Polygon search that has no intersections with the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon (apply st/search-poly intersects-none)}))))
      (testing "Polygon search that intersects with the bounding-box of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon (apply st/search-poly intersects-bbox)}))))
      (testing "Polygon search that intersects with one polygon of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon (apply st/search-poly intersects-polygon)}))))

      (testing "Polygon search that intersects with all geometries of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon (apply st/search-poly intersects-both)})))))

    (testing "Polygon search with 'ignore-br' flag"
      (comment)
      (testing "Polygon search that has no intersections with the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:ignore-br (apply st/search-poly intersects-none)}}))))
      (testing "Polygon search that intersects with the bounding-box of the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:ignore-br (apply st/search-poly intersects-bbox)}}))))
      (testing "Polygon search that intersects with the one polygon of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:ignore-br (apply st/search-poly intersects-polygon)}}))))

      (testing "Polygon search that intersects with all geometries of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:ignore-br (apply st/search-poly intersects-both)}})))))

    (testing "Polygon search with 'every' flag"
      (comment)
      (testing "Polygon search that has no intersections with the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:every (apply st/search-poly intersects-none)}}))))
      (testing "Polygon search that intersects with the bounding-box of the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:every (apply st/search-poly intersects-bbox)}}))))
      (testing "Polygon search that intersects with the one polygon of the granule"
        (= 0 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:every (apply st/search-poly intersects-polygon)}}))))

      (testing "Polygon search that intersects with all geometries of the granule"
        (= 1 (:hits (search/find-refs
                           :granule
                           {:provider "PROV1"
                            :polygon {:every (apply st/search-poly intersects-both)}})))))))
