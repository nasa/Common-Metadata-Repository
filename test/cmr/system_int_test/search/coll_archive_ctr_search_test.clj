(ns ^{:doc "Search CMR Collections by ArchiveCenter"}
  cmr.system-int-test.search.coll-archive-ctr-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture "CMR_PROV1" "CMR_PROV2"))

(deftest search-colls-by-archive-center-names
  (let [coll1 (d/ingest "CMR_PROV1" (dc/collection {}))
        coll2 (d/ingest "CMR_PROV1" (dc/collection {:organizations (dc/orgs [])}))
        coll3 (d/ingest "CMR_PROV1" (dc/collection {:organizations (dc/orgs [{:type "processing-center" :org-name "Larc"}])}))
        coll4 (d/ingest "CMR_PROV1" (dc/collection {:organizations (dc/orgs [{:type "archive-center" :org-name "Larc"}])}))

        coll5 (d/ingest "CMR_PROV2" (dc/collection {:organizations (dc/orgs [{:type "archive-center" :org-name "SEDAC AC"}
                                                                {:type "processing-center" :org-name "SEDAC PC"}])}))
        coll6 (d/ingest "CMR_PROV2" (dc/collection {:organizations (dc/orgs [{:type "archive-center" :org-name "Larc"}])}))

        coll7 (d/ingest "CMR_PROV2" (dc/collection {:organizations (dc/orgs [{:type "archive-center" :org-name "Sedac AC"}
                                                                {:type "processing-center" :org-name "Sedac"}])}))]

    (index/flush-elastic-index)

    (testing "search coll by archive center"
      (are [org-name items] (d/refs-match? items (search/find-refs :collection {:archive-center org-name}))
           "Larc" [coll4 coll6]
           "SEDAC AC" [coll5]
           "SEDAC PC" []
           "Sedac AC" [coll7]
           "BLAH" []))
    (testing "case sensitivity ..."
      (are [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
           {:archive-center "Sedac AC", "options[archive-center][ignore_case]" "false"} [coll7]
           {:archive-center "sedac ac", "options[archive-center][ignore_case]" "true"} [coll5 coll7]))
    (testing "search using wild cards"
      (is (d/refs-match? [coll5 coll7]
                         (search/find-refs :collection {:archive-center "S*", "options[archive-center][pattern]" "true"}))))
    (testing "search using AND/OR operators"
      (are [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
           {"archive-center[]" ["SEDAC AC" "Larc"], "options[archive-center][and]" "true"} []
           {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"], "options[archive-center][and]" "false"} [coll4 coll5 coll6 coll7]
           {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"]} [coll4 coll5 coll6 coll7]))))

