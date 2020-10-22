(ns cmr.system-int-test.search.collection-boolean-flag-search-test
  "Integration tests for searching by downloadable, browsable, and has-opendap-url"
  (:require
   [clojure.test :refer :all]
   [cmr.system-int-test.data2.core :as d]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest search-collection-by-downloadable
  (let [ru1 (data-umm-cmn/related-url {:URLContentType "DistributionURL"
                                       :Type "GET DATA"})
        ru2 (data-umm-cmn/related-url {:URLContentType "VisualizationURL"
                                       :Type "GET RELATED VISUALIZATION"})
        ru3 (data-umm-cmn/related-url {:URLContentType "PublicationURL"
                                       :Type "VIEW RELATED INFORMATION"})
        ru4 (data-umm-cmn/related-url {:URLContentType "DistributionURL"
                                       :Type "USE SERVICE API"
                                       :Subtype "OPENDAP DATA"})
        coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {:RelatedUrls [ru1]}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:RelatedUrls [ru2]}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:RelatedUrls [ru3]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:RelatedUrls [ru2 ru3]}))
        coll5 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 5 {:RelatedUrls [ru1 ru2 ru4]}))
        coll6 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 6 {}))]
    (index/wait-until-indexed)

    (testing "search by downloadable flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:downloadable value}))
         [coll1 coll5] true
         [coll2 coll3 coll4 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by downloadable wrong value"
      (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:downloadable "wrong"}))))

    (testing "search by online only flag"
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:online-only value}))
         [coll1 coll5] true
         [coll1 coll5] "True"
         [coll2 coll3 coll4 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by online only wrong value"
     (is (= {:status 400 :errors ["Parameter downloadable must take value of true, false, or unset, but was [wrong]"]}
            (search/find-refs :collection {:online-only "wrong"}))))

    (testing "search by online only with aql"
      (are [items value]
           (d/refs-match? items
                          (search/find-refs-with-aql :collection [{:onlineOnly value}]))
           ;; it is not possible to search onlineOnly false in AQL, so we don't have a test for that
           [coll1 coll5] true
           [coll1 coll5] nil))


    (testing "search by browsable flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:browsable value}))
         [coll2 coll4 coll5] true
         [coll1 coll3 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by browsable wrong value"
      (is (= {:status 400 :errors ["Parameter browsable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:browsable "wrong"}))))

    (testing "search by browse_only flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:browse-only value}))
         [coll2 coll4 coll5] true
         [coll1 coll3 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by browse_only wrong value"
      (is (= {:status 400 :errors ["Parameter browsable must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:browse-only "wrong"}))))

    (testing "search by has-opendap-url flag."
      (are [items value]
         (d/refs-match? items (search/find-refs :collection {:has-opendap-url value}))
         [coll5] true
         [coll1 coll2 coll3 coll4 coll6] false
         [coll1 coll2 coll3 coll4 coll5 coll6] "unset"))

    (testing "search by has-opendap-url wrong value"
      (is (= {:status 400 :errors ["Parameter has_opendap_url must take value of true, false, or unset, but was [wrong]"]}
             (search/find-refs :collection {:has-opendap-url "wrong"}))))))
