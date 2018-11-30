(ns cmr.system-int-test.search.collection-identifier-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as s]
   [clojure.test :refer :all]
   [cmr.common-app.services.search.messages :as cmsg]
   [cmr.common.services.messages :as msg]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))


(comment
 (do
   ((ingest/reset-fixture {"provguid1" "PROV1"}) (constantly "done"))
   (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection
                      {:ShortName (str "S" 1)
                       :Version (str "V" 1)
                       :EntryTitle (str "ET" 1)}))))


(deftest identifier-search-test

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry_title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2] (doall (for [p ["PROV1" "PROV2"]
                                               n (range 1 5)]
                                           (d/ingest-umm-spec-collection
                                            p
                                            (data-umm-c/collection
                                             {:ShortName (str "S" n)
                                              :Version (str "V" n)
                                              :EntryTitle (str "ET" n)}))))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)]
    (index/wait-until-indexed)

    (testing "concept id"
      (are [items ids]
           (d/refs-match? items (search/find-refs :collection {:concept-id ids}))

           [c1-p1] (:concept-id c1-p1)
           [c1-p2] (:concept-id c1-p2)
           [c1-p1 c1-p2] [(:concept-id c1-p1) (:concept-id c1-p2)]
           [c1-p1] [(:concept-id c1-p1) "C2200-PROV1"]
           [c1-p1] [(:concept-id c1-p1)]
           [] "FOO"))

    (testing "Concept id search using JSON query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [c1-p1] {:concept_id (:concept-id c1-p1)}
           [c1-p2] {:concept_id (:concept-id c1-p2)}
           [c1-p1 c1-p2] {:or [{:concept_id (:concept-id c1-p1)}
                               {:concept_id (:concept-id c1-p2)}]}
           [c1-p1] {:or [{:concept_id (:concept-id c1-p1)}
                         {:concept_id "C2200-PROV1"}]}
           [c1-p1] {:or [{:concept_id (:concept-id c1-p1)}
                         {:concept_id "FOO"}]}
           [] {:concept_id "FOO"}))

    (testing "provider with parameters"
      (are [items p options]
           (let [params (merge {:provider p}
                               (when options
                                 {"options[provider]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           all-prov1-colls "PROV1" {}
           all-prov2-colls "PROV2" {}
           [] "PROV3" {}

           ;; Multiple values
           all-colls ["PROV1" "PROV2"] {}
           all-prov1-colls ["PROV1" "PROV3"] {}

           ;; Wildcards
           all-colls "PROV*" {:pattern true}
           [] "PROV*" {:pattern false}
           [] "PROV*" {}
           all-prov1-colls "*1" {:pattern true}
           all-prov1-colls "P?OV1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           all-prov1-colls "pRoV1" {}
           all-prov1-colls "pRoV1" {:ignore-case true}
           [] "prov1" {:ignore-case false}))

    (testing "legacy catalog rest parameter name"
      (is (d/refs-match? all-prov1-colls (search/find-refs :collection {:provider-id "PROV1"}))))

    (testing "provider with aql"
      (are [items provider-ids options]
           (let [data-center-condition (merge {:dataCenterId provider-ids} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [] data-center-condition)))

           all-prov1-colls ["PROV1"] {}
           all-prov1-colls ["'PROV1'"] {}
           all-prov2-colls ["PROV2"] {}
           [] ["PROV3"] {}

           ;; Multiple values
           all-colls ["PROV1" "PROV2"] {}
           all-prov1-colls ["PROV1" "PROV3"] {}
           all-prov1-colls ["'PROV1'" "'PROV3'"] {}

           ;; Ignore case
           [] "pRoV1" {}
           all-prov1-colls "pRoV1" {:ignore-case true}
           [] "prov1" {:ignore-case false}))

    (testing "Provider search using JSON query"
      (are [items json-search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} json-search))

           all-prov1-colls {:provider "PROV1"}
           all-prov2-colls {:provider "PROV2"}
           [] {:provider "PROV3"}

           ;; Multiple values
           all-colls {:or [{:provider "PROV1"}
                           {:provider "PROV2"}]}
           all-prov1-colls {:or [{:provider "PROV1"}
                                 {:provider "PROV3"}]}

           ;; In combination with 'not'
           all-prov2-colls {:not {:provider "PROV1"}}
           all-prov1-colls {:not {:provider "PROV2"}}

           ;; Wildcards
           all-colls {:provider {:value "PROV*" :pattern true}}
           [] {:provider {:value "PROV*" :pattern false}}
           [] {:provider {:value "PROV*"}}
           all-prov1-colls {:provider {:value "*1" :pattern true}}
           all-prov1-colls {:provider {:value "P?OV1" :pattern true}}
           [] {:provider {:value "*Q*" :pattern true}}

           ;; Ignore case
           all-prov1-colls {:provider {:value "pRoV1"}}
           all-prov1-colls {:provider {:value "pRoV1" :ignore_case true}}
           [] {:provider {:value "prov1" :ignore_case false}}
           all-colls {:not {:provider {:value "prov1" :ignore_case false}}}))

    (testing "short name"
      (are [items sn options]
           (let [params (merge {:short-name sn}
                               (when options
                                 {"options[short-name]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1 c1-p2] "S1" {}
           [] "S44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["S1" "S2"] {}
           [c1-p1 c1-p2] ["S1" "S44"] {}
           [c1-p1 c1-p2 c2-p1 c2-p2] ["S1" "S2"] {:and false}
           [] ["S1" "S2"] {:and true}

           ;; Wildcards
           all-colls "S*" {:pattern true}
           [] "S*" {:pattern false}
           [] "S*" {}
           [c1-p1 c1-p2] "*1" {:pattern true}
           [c1-p1 c1-p2] "?1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "s1" {}
           [c1-p1 c1-p2] "s1" {:ignore-case true}
           [] "s1" {:ignore-case false}))

    (testing "shortName with aql"
      (are [items sn options]
           (let [condition (merge {:shortName sn} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection [condition])))

           [c1-p1 c1-p2] "S1" {}
           [] "S44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["S1" "S2"] {}
           [c1-p1 c1-p2] ["S1" "S44"] {}

           ;; Wildcards
           all-colls "S%" {:pattern true}
           [] "S%" {:pattern false}
           [] "S%" {}
           [c1-p1 c1-p2] "%1" {:pattern true}
           [c1-p1 c1-p2] "_1" {:pattern true}
           all-colls "S%" {:pattern true}
           [c1-p1 c1-p2] "_1" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [] "s1" {}
           [c1-p1 c1-p2] "s1" {:ignore-case true}
           [] "s1" {:ignore-case false}))

    (testing "Short name using JSON query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [c1-p1 c1-p2] {:short_name "S1"}
           [] {:short_name "S44"}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:short_name "S1"} {:short_name "S2"}]}
           [c1-p1 c1-p2] {:or [{:short_name "S1"} {:short_name "S44"}]}
           [] {:and [{:short_name "S1"} {:short_name "S2"}]}

           ;; Wildcards
           all-colls {:short_name {:value "S*" :pattern true}}
           [] {:short_name {:value "S*" :pattern false}}
           [] {:short_name {:value "S*"}}
           [c1-p1 c1-p2] {:short_name {:value "*1" :pattern true}}
           [c1-p1 c1-p2] {:short_name {:value "?1" :pattern true}}
           [] {:short_name {:value "*Q*" :pattern true}}

           ;; Ignore case
           [c1-p1 c1-p2] {:short_name {:value "s1"}}
           [c1-p1 c1-p2] {:short_name {:value "s1" :ignore_case true}}
           [] {:short_name {:value "s1" :ignore_case false}}))

    (testing "version"
      (are [items v options]
           (let [params (merge {:version v}
                               (when options
                                 {"options[version]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1 c1-p2] "V1" {}
           [] "V44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["V1" "V2"] {}
           [c1-p1 c1-p2] ["V1" "V44"] {}

           ;; Wildcards
           all-colls "V*" {:pattern true}
           [] "V*" {:pattern false}
           [] "V*" {}
           [c1-p1 c1-p2] "*1" {:pattern true}
           [c1-p1 c1-p2] "?1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "v1" {:ignore-case true}
           [] "v1" {:ignore-case false}))

    (testing "versionId with aql"
      (are [items v options]
           (let [condition (merge {:versionId v} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [c1-p1 c1-p2] "V1" {}
           [] "V44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["V1" "V2"] {}
           [c1-p1 c1-p2] ["V1" "V44"] {}

           ;; Wildcards
           all-colls "V%" {:pattern true}
           [] "V%" {:pattern false}
           [] "V%" {}
           [c1-p1 c1-p2] "%1" {:pattern true}
           [c1-p1 c1-p2] "_1" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "v1" {:ignore-case true}
           [] "v1" {:ignore-case false}))

    (testing "Version using JSON query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [c1-p1 c1-p2] {:version "V1"}
           [] {:version "V44"}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:version "V1"} {:version "V2"}]}
           [c1-p1 c1-p2] {:or [{:version "V1"} {:version "V44"}]}
           [] {:and [{:version "V1"} {:version "V2"}]}

           ;; Wildcards
           all-colls {:version {:value "V*" :pattern true}}
           [] {:version {:value "V*" :pattern false}}
           [] {:version {:value "V*"}}
           [c1-p1 c1-p2] {:version {:value "*1" :pattern true}}
           [c1-p1 c1-p2] {:version {:value "?1" :pattern true}}
           [] {:version {:value "*Q*" :pattern true}}

           ;; Ignore case
           [c1-p1 c1-p2] {:version {:value "v1" :ignore_case true}}
           [] {:version {:value "v1" :ignore_case false}}))

    (testing "Entry id"
      (are [items ids options]
           (let [params (merge {:entry-id ids}
                               (when options
                                 {"options[entry-id]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1 c1-p2] "S1_V1" {}
           [] "S44_V44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["S1_V1" "S2_V2"] {}
           [c1-p1 c1-p2] ["S1_V1" "S44_V44"] {}
           [c1-p1 c1-p2 c2-p1 c2-p2] ["S1_V1" "S2_V2"] {:and false}
           [] ["S1_V1" "S2_V2"] {:and true}

           ;; Wildcards
           all-colls "S*_V*" {:pattern true}
           [] "S*_V*" {:pattern false}
           [] "S*_V*" {}
           [c1-p1 c1-p2] "*1" {:pattern true}
           [c1-p1 c1-p2] "S1_?1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "S1_v1" {:ignore-case true}
           [] "S1_v1" {:ignore-case false}))

    (testing "Entry id search using JSON Query"
      (are [items json-search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} json-search))

           [c1-p1 c1-p2] {:entry_id "S1_V1"}
           [] {:entry_id "S44_V44"}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:entry_id "S1_V1"}
                                           {:entry_id "S2_V2"}]}
           [c1-p1 c1-p2] {:or [{:entry_id "S1_V1"}
                               {:entry_id "S44_V44"}]}
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:entry_id "S1_V1"}
                                           {:entry_id "S2_V2"}]}
           [] {:and [{:entry_id "S1_V1"}
                     {:entry_id "S2_V2"}]}

           ;; Not with multiple entry-ids
           [c3-p1 c3-p2 c4-p1 c4-p2] {:not {:or [{:entry_id "S2_V2"}
                                                 {:entry_id "S1_V1"}]}}

           ;; Not with multiple entry-ids and provider
           [c3-p1 c4-p1] {:not {:or [{:entry_id "S2_V2"}
                                     {:entry_id "S1_V1"}
                                     {:provider "PROV2"}]}}

           ;; Wildcards
           all-colls {:entry_id {:value "S*_V*" :pattern true}}
           [] {:entry_id {:value "S*_V*" :pattern false}}
           [] {:entry_id {:value "S*_V*"}}
           [c1-p1 c1-p2] {:entry_id {:value "*1" :pattern true}}
           [c1-p1 c1-p2] {:entry_id {:value "S1_?1" :pattern true}}
           [] {:entry_id {:value "*Q*" :pattern true}}

           ;; Ignore case
           [c1-p1 c1-p2] {:entry_id {:value "S1_v1" :ignore_case true}}
           [] {:entry_id {:value "S1_v1" :ignore_case false}}))

    (testing "Entry title"
      (are [items v options]
           (let [params (merge {:entry_title v}
                               (when options
                                 {"options[entry-title]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1 c1-p2] "ET1" {}
           [] "ET44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["ET1" "ET2"] {}
           [c1-p1 c1-p2] ["ET1" "ET44"] {}
           [c1-p1 c1-p2 c2-p1 c2-p2] ["ET1" "ET2"] {:and false}
           [] ["ET1" "ET2"] {:and true}

           ;; Wildcards
           all-colls "ET*" {:pattern true}
           [] "ET*" {:pattern false}
           [] "ET*" {}
           [c1-p1 c1-p2] "*1" {:pattern true}
           [c1-p1 c1-p2] "?T1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "et1" {:ignore_case true}
           [] "et1" {:ignore_case false})

      (is (d/refs-match?
            [c1-p1 c1-p2]
            (search/find-refs :collection {:dataset-id "ET1"}))
          "dataset_id should be an alias for entry title."))

    (testing "Entry title search using JSON Query"
      (are [items json-search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} json-search))

           [c1-p1 c1-p2] {:entry_title "ET1"}
           [] {:entry_title "ET44"}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:entry_title "ET1"}
                                           {:entry_title "ET2"}]}
           [c1-p1 c1-p2] {:or [{:entry_title "ET1"}
                               {:entry_title "ET44"}]}
           [c1-p1 c1-p2 c2-p1 c2-p2] {:or [{:entry_title "ET1"}
                                           {:entry_title "ET2"}]}
           [] {:and [{:entry_title "ET1"}
                     {:entry_title "ET2"}]}

           ;; Wildcards
           all-colls {:entry_title {:value "ET*" :pattern true}}
           [] {:entry_title {:value "ET*" :pattern false}}
           [] {:entry_title {:value "ET*"}}
           [c1-p1 c1-p2] {:entry_title {:value "*1" :pattern true}}
           [c1-p1 c1-p2] {:entry_title {:value "?T1" :pattern true}}
           [] {:entry_title {:value "*Q*" :pattern true}}

           ;; Ignore case
           [c1-p1 c1-p2] {:entry_title {:value "et1" :ignore_case true}}
           [] {:entry_title {:value "et1" :ignore_case false}}))

    (testing "dataSetId with aql"
      (are [items v options]
           (let [condition (merge {:dataSetId v} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [c1-p1 c1-p2] "ET1" {}
           [] "ET44" {}
           ;; Multiple values
           [c1-p1 c1-p2 c2-p1 c2-p2] ["ET1" "ET2"] {}
           [c1-p1 c1-p2] ["ET1" "ET44"] {}

           ;; Wildcards
           all-colls "ET%" {:pattern true}
           [] "ET%" {:pattern false}
           [] "ET%" {}
           [c1-p1 c1-p2] "%1" {:pattern true}
           [c1-p1 c1-p2] "_T1" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "et1" {:ignore-case true}
           [] "et1" {:ignore-case false}))

    (testing "unsupported parameter"
      (is (= {:status 400,
              :errors ["Parameter [unsupported] was not recognized."]}
             (search/find-refs :collection {:unsupported "dummy"})))
      (is (= {:status 400,
              :errors ["Parameter [unsupported] with option was not recognized."]}
             (search/find-refs :collection {"options[unsupported][ignore-case]" true})))
      (is (= {:status 400,
              :errors [(cmsg/invalid-opt-for-param :entry_title :unsupported)]}
             (search/find-refs
               :collection
               {:entry_title "dummy" "options[entry-title][unsupported]" "unsupported"}))))

    (testing "empty parameters are ignored"
      (is (d/refs-match? [c1-p1] (search/find-refs :collection {:concept-id (:concept-id c1-p1)
                                                                :short-name ""
                                                                :version "    "
                                                                :entry_title "  \n \t"}))))))

;; Create 2 collection sets of which only 1 set has processing-level-id
(deftest processing-level-search-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1] (doall (for [n (range 1 5)]
                                           (d/ingest-umm-spec-collection
                                            "PROV1" (data-umm-c/collection n {}))))
        ;; include processing level id
        [c1-p2 c2-p2 c3-p2 c4-p2] (doall (for [n (range 1 5)]
                                           (d/ingest-umm-spec-collection
                                            "PROV2" (data-umm-c/collection n
                                            {:ProcessingLevel (umm-c/map->ProcessingLevelType {:Id (str n "B")})}))))
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]]
    (index/wait-until-indexed)
    (testing "processing level search"
      (are [items id options]
           (let [params (merge {:processing-level-id id}
                               (when options
                                 {"options[processing-level-id]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p2] "1B" {}
           [] "1C" {}
           ;; Multiple values
           [c1-p2 c2-p2 c3-p2] ["1B" "2B" "3B"] {}
           [c4-p2] ["4B" "4C"] {}

           ;; Wildcards
           all-prov2-colls "*B" {:pattern true}
           [] "B*" {:pattern false}
           [] "B*" {}
           all-prov2-colls "?B" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           [c2-p2] "2b" {:ignore-case true}
           [] "2b" {:ignore-case false}))

    (testing "search with legacy processing-level"
      (is (d/refs-match? [c1-p2 c2-p2 c3-p2]
                         (search/find-refs :collection {:processing-level ["1B" "2B" "3B"]}))))

    (testing "processing level search with aql"
      (are [items id options]
           (let [condition (merge {:processingLevel id} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [c1-p2] "1B" {}
           [] "1C" {}
           ;; Multiple values
           [c1-p2 c2-p2 c3-p2] ["1B" "2B" "3B"] {}
           [c4-p2] ["4B" "4C"] {}

           ;; Wildcards
           all-prov2-colls "%B" {:pattern true}
           [] "B%" {:pattern false}
           [] "B%" {}
           all-prov2-colls "_B" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c2-p2] "2b" {:ignore-case true}
           [] "2b" {:ignore-case false}))

    (testing "Processing level id search using JSON Query"
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [c1-p2] {:processing_level_id "1B"}
           [] {:processing_level_id "1C"}
           ;; Multiple values
           [c1-p2 c2-p2 c3-p2] {:or [{:processing_level_id "1B"}
                                     {:processing_level_id "2B"}
                                     {:processing_level_id "3B"}]}
           [c4-p2] {:or [{:processing_level_id "4B"} {:processing_level_id "4C"}]}
           [c1-p1 c2-p1 c3-p1 c4-p1 c4-p2] {:not {:or [{:processing_level_id "1B"}
                                                       {:processing_level_id "2B"}
                                                       {:processing_level_id "3B"}]}}

           ;; Wildcards
           all-prov2-colls {:processing_level_id {:value "*B" :pattern true}}
           [] {:processing_level_id {:value "B*" :pattern false}}
           [] {:processing_level_id {:value "B*"}}
           all-prov2-colls {:processing_level_id {:value "?B" :pattern true}}
           [] {:processing_level_id {:value "*Q*" :pattern true}}

           ;; Ignore case
           [c2-p2] {:processing_level_id {:value "2b" :ignore_case true}}
           [] {:processing_level_id {:value "2b" :ignore_case false}}))))

;; Find collections by echo_collection_id and concept_id params
(deftest echo-coll-id-search-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2] (doall (for [p ["PROV1" "PROV2"]
                                               n (range 1 5)]
                                           (d/ingest-umm-spec-collection
                                            p (data-umm-c/collection n {}))))
        c1-p1-cid (get-in c1-p1 [:concept-id])
        c2-p1-cid (get-in c2-p1 [:concept-id])
        c3-p2-cid (get-in c3-p2 [:concept-id])
        c4-p2-cid (get-in c4-p2 [:concept-id])
        dummy-cid "C1000000004-PROV2"
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)]
    (index/wait-until-indexed)
    (testing "echo collection id search"
      (are [items cid options]
           (let [params (merge {:echo_collection_id cid}
                               (when options
                                 {"options[echo_collection_id]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1] c1-p1-cid {}
           [c3-p2] c3-p2-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [c1-p1 c2-p1 c3-p2 c4-p2] [c1-p1-cid c2-p1-cid c3-p2-cid c4-p2-cid dummy-cid] {}))
    (testing "echo collection id search - disallow ignore case"
      (is (= {:status 400
              :errors [(cmsg/invalid-opt-for-param :concept-id :ignore-case)]}
             (search/find-refs :granule {:echo_collection_id c2-p1-cid "options[echo_collection_id]" {:ignore_case true}}))))
    (testing "Search with wildcards in echo_collection_id param not supported."
      (is (= {:errors
               ["Concept-id [C*] is not valid."
                "Option [pattern] is not supported for param [concept_id]"],
               :status 400}
             (search/find-refs :granule {:echo_collection_id "C*" "options[echo_collection_id]" {:pattern true}}))))
    (testing "concept id search"
      ;; skipping some test conditions because concept_id search is similar in behavior to above echo_collection_id search
      (are [items cid options]
           (let [params (merge {:concept_id cid}
                               (when options
                                 {"options[concept_id]" options}))]
             (d/refs-match? items (search/find-refs :collection params)))

           [c1-p1] c1-p1-cid {}
           [c3-p2] c3-p2-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [c1-p1 c2-p1 c3-p2 c4-p2] [c1-p1-cid c2-p1-cid c3-p2-cid c4-p2-cid dummy-cid] {}
           [] [c1-p1-cid  c3-p2-cid] {:and true}))
    (testing "Search with wildcards in concept_id param not supported."
      (is (= {:errors
               ["Concept-id [C*] is not valid."
                "Option [pattern] is not supported for param [concept_id]"],
               :status 400}
             (search/find-refs :granule {:concept_id "C*" "options[concept_id]" {:pattern true}}))))

    (testing "echo collection id search with aql"
      (are [items cid options]
           (let [condition (merge {:ECHOCollectionID cid} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection [condition])))

           [c1-p1] c1-p1-cid {}
           [c3-p2] c3-p2-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [c1-p1 c2-p1 c3-p2 c4-p2] [c1-p1-cid c2-p1-cid c3-p2-cid c4-p2-cid dummy-cid] {}))))

(deftest search-by-too-many-conditions
  (testing "Query with many concept ids is not rejected"
    (let [response (search/find-refs
                    :collection
                    {:concept-id (for [n (range 3000)]
                                   (str "C" n "-PROV1"))}
                    {:method :post})]
      (is (= {:hits 0 :refs []}
             (select-keys response [:hits :refs])))))
  (testing "Query with too many conditions"
    (is (= {:errors ["The number of conditions in the query [6000] exceeded the maximum allowed for a query [4100]. Reduce the number of conditions in your query."]
            :status 400}
           (search/find-refs
            :collection
            {:science-keywords (into {} (for [n (range 2000)]
                                          [(keyword (str n))
                                           {:category n
                                            :topic n
                                            :term n}]))}
            {:method :post})))))


(deftest search-with-slashes-in-dataset-id
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:EntryTitle "Dataset1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:EntryTitle "Dataset/With/Slashes"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:EntryTitle "Dataset3"}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:EntryTitle "Dataset/With/More/Slashes"}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {}))]

    (index/wait-until-indexed)

    (testing "search for dataset with slashes"
      (are [dataset-id items] (d/refs-match? items (search/find-refs :collection {:dataset-id dataset-id}))
           "Dataset/With/Slashes" [coll2]
           "BLAH" []))))

(deftest search-with-invalid-escaped-param
  (testing "CMR-1192: Searching with invalid escaped character returns internal error"
    ;; I am not able to find an easy way to submit a http request with an invalid url to bypass the
    ;; client side checking. So we do it through curl on the command line.
    ;; This depends on curl being installed on the test machine.
    ;; Do not use this unless absolutely necessary.
    (let [{:keys [out]} (shell/sh "curl" "--silent" "-i"
                                  (str (url/search-url :collection) "?entry-title\\[\\]=%"))]
      (is (re-find #"(?s)400 Bad Request.*Invalid URL encoding: Incomplete trailing escape \(%\) pattern.*" out)))))
