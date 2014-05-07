(ns cmr.system-int-test.search.collection-identifier-search-test
  "Tests searching for collections using basic collection identifiers"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))


(use-fixtures :each (ingest/reset-fixture "PROV1" "PROV2"))


(deftest identifier-search-test

  ;; Create 4 collections in each provider that are identical.
  ;; The first collection will have data:
  ;; {:entry-id "S1_V1", :entry-title "ET1", :short-name "S1", :version-id "V1"}
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2] (for [p ["PROV1" "PROV2"]
                                        n (range 1 5)]
                                    (d/ingest p (dc/collection
                                                  {:short-name (str "S" n)
                                                   :version-id (str "V" n)
                                                   :entry-title (str "ET" n)})))
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)]
    (index/flush-elastic-index)

    (testing "concept id"
      (are [items ids]
           (d/refs-match? items (search/find-refs :collection {:concept-id ids}))

           [c1-p1] (:concept-id c1-p1)
           [c1-p2] (:concept-id c1-p2)
           [c1-p1 c1-p2] [(:concept-id c1-p1) (:concept-id c1-p2)]
           [c1-p1] [(:concept-id c1-p1) "C2200-PROV1"]
           [c1-p1] [(:concept-id c1-p1) "FOO"]
           [] "FOO"))

    (testing "provider"
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
           all-colls ["PROV1" "PROV2"] {:and false}
           [] ["PROV1" "PROV2"] {:and true}

           ;; Wildcards
           all-colls "PROV*" {:pattern true}
           [] "PROV*" {:pattern false}
           [] "PROV*" {}
           all-prov1-colls "*1" {:pattern true}
           all-prov1-colls "P?OV1" {:pattern true}
           [] "*Q*" {:pattern true}

           ;; Ignore case
           all-prov1-colls "pRoV1" {:ignore-case true}
           [] "prov1" {:ignore-case false}))

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
           [c1-p1 c1-p2] "s1" {:ignore-case true}
           [] "s1" {:ignore-case false}))

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
           [c1-p1 c1-p2 c2-p1 c2-p2] ["V1" "V2"] {:and false}
           [] ["V1" "V2"] {:and true}

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

    (testing "entry id"
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
           [] "S1_v1" {:ignore-case false})

      (is (d/refs-match?
            [c1-p1 c1-p2]
            (search/find-refs :collection {:dif-entry-id "S1_V1"}))
          "dif_entry_id should be an alias for entry id"))

    (testing "Entry title"
      (are [items v options]
           (let [params (merge {:entry-title v}
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
           [c1-p1 c1-p2] "et1" {:ignore-case true}
           [] "et1" {:ignore-case false})

      (is (d/refs-match?
            [c1-p1 c1-p2]
            (search/find-refs :collection {:dataset-id "ET1"}))
          "dataset_id should be an alias for entry title."))

    (testing "unsupported parameter"
      (is (= {:status 422,
              :errors ["Parameter [unsupported] was not recognized."]}
             (search/find-refs :collection {:unsupported "dummy"})))
      (is (= {:status 422,
              :errors ["Parameter [unsupported] with option was not recognized."]}
             (search/find-refs :collection {"options[unsupported][ignore-case]" true})))
      (is (= {:status 422,
              :errors ["Option [unsupported] for param [entry_title] was not recognized."]}
             (search/find-refs
               :collection
               {:entry-title "dummy" "options[entry-title][unsupported]" "unsupported"}))))))


