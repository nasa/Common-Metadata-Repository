(ns cmr.system-int-test.aql.collection-identifier-search-test
  "Tests searching for collections using aql basic collection identifiers"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [cmr.common.services.messages :as msg]
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
    (index/refresh-elastic-index)

    (testing "provider"
      (are [items provider-ids options]
           (let [params (merge {:provider-ids provider-ids} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection params)))

           all-prov1-colls ["PROV1"] {}
           all-prov2-colls ["PROV2"] {}
           [] ["PROV3"] {}

           ;; Multiple values
           all-colls ["PROV1" "PROV2"] {}
           all-prov1-colls ["PROV1" "PROV3"] {}

           ;; Wildcards
           all-colls "PROV%" {:pattern true}
           [] "PROV%" {:pattern false}
           [] "PROV%" {}
           all-prov1-colls "%1" {:pattern true}
           all-prov1-colls "P_OV1" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           all-prov1-colls "pRoV1" {:ignore-case true}
           [] "prov1" {:ignore-case false}))

    (testing "shortName"
      (are [items sn options]
           (let [params (merge {:where {:short-name sn}} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection params)))

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
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "s1" {:ignore-case true}
           [] "s1" {:ignore-case false}))

    (testing "versionId"
      (are [items v options]
           (let [params (merge {:where {:version-id v}} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection params)))

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

    (testing "dataSetId"
      (are [items v options]
           (let [params (merge {:where {:entry-title v}} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection params)))

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
           [c1-p1 c1-p2] "?T1" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c1-p1 c1-p2] "et1" {:ignore-case true}
           [] "et1" {:ignore-case false}))))

;; Create 2 collection sets of which only 1 set has processing-level-id
(deftest processing-level-search-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1] (for [n (range 1 5)]
                                    (d/ingest "PROV1" (dc/collection {})))
        ;; include processing level id
        [c1-p2 c2-p2 c3-p2 c4-p2] (for [n (range 1 5)]
                                    (d/ingest "PROV2" (dc/collection {:processing-level-id (str n "B")})))
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]]
    (index/refresh-elastic-index)
    (testing "processing level search"
      (are [items id options]
           (let [params (merge {:where {:processing-level-id id}} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection params)))

           [c1-p2] "1B" {}
           [] "1C" {}
           ;; Multiple values
           [c1-p2 c2-p2 c3-p2] ["1B" "2B" "3B"] {}
           [c4-p2] ["4B" "4C"] {}

           ;; Wildcards
           all-prov2-colls "%B" {:pattern true}
           [] "B%" {:pattern false}
           [] "B%" {}
           all-prov2-colls "?B" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [c2-p2] "2b" {:ignore-case true}
           [] "2b" {:ignore-case false}))))

(deftest echo-coll-id-search-test
  (let [[c1-p1 c2-p1 c3-p1 c4-p1
         c1-p2 c2-p2 c3-p2 c4-p2] (for [p ["PROV1" "PROV2"]
                                        n (range 1 5)]
                                    (d/ingest p (dc/collection {})))
        c1-p1-cid (get-in c1-p1 [:concept-id])
        c2-p1-cid (get-in c2-p1 [:concept-id])
        c3-p2-cid (get-in c3-p2 [:concept-id])
        c4-p2-cid (get-in c4-p2 [:concept-id])
        dummy-cid "D1000000004-PROV2"
        all-prov1-colls [c1-p1 c2-p1 c3-p1 c4-p1]
        all-prov2-colls [c1-p2 c2-p2 c3-p2 c4-p2]
        all-colls (concat all-prov1-colls all-prov2-colls)]
    (index/refresh-elastic-index)
    (testing "echo collection id search"
      (are [items cid options]
           (let [params (merge {:where {:echo-collection-id cid}} options)]
             (d/refs-match? items (search/find-refs-with-aql :collection params)))

           [c1-p1] c1-p1-cid {}
           [c3-p2] c3-p2-cid {}
           [] dummy-cid {}
           ;; Multiple values
           [c1-p1 c2-p1 c3-p2 c4-p2] [c1-p1-cid c2-p1-cid c3-p2-cid c4-p2-cid dummy-cid] {}))))

(deftest dif-entry-id-search-test
  (let [coll1 (d/ingest "PROV1" (dc/collection {:short-name "S1"
                                                :version-id "V1"}))
        coll2 (d/ingest "PROV1" (dc/collection {:entry-id "S2"}) :dif)
        coll3 (d/ingest "PROV2" (dc/collection {:associated-difs ["S3"]}))
        coll4 (d/ingest "PROV2" (dc/collection {:associated-difs ["SL4" "DIF-1"]}))
        coll5 (d/ingest "PROV2" (dc/collection {:entry-id "T2"}) :dif)]
    (index/refresh-elastic-index)
    (testing "dif entry id search"
      (are [items id options]
           (let [params (merge {:where {:dif-entry-id id}} options)]
           (d/refs-match? items (search/find-refs-with-aql :collection params)))

           [coll1] "S1_V1" {}
           [coll2] "S2" {}
           [coll3] "S3" {}
           [] "S1" {}
           ;; Multiple values
           [coll2 coll3] ["S2" "S3"] {}
           [coll4] ["SL4" "DIF-1"] {}

           ;; Wildcards
           [coll1 coll2 coll3 coll4] "S%" {:pattern true}
           [] "S%" {:pattern false}
           [] "S%" {}
           [coll2 coll3] "S?" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [coll2] "s2" {:ignore-case true}
           [] "s2" {:ignore-case false}))))


