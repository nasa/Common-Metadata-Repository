(ns ^{:doc "Search CMR Collections by ArchiveCenter"}
  cmr.system-int-test.search.coll-archive-ctr-search-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.search-util :as search]
            [cmr.system-int-test.utils.index-util :as index]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-colls-by-archive-center-names
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {:organizations []}))
        coll3 (d/ingest "PROV1" (dc/collection {:organizations [(dc/org :processing-center "Larc")]}))
        coll4 (d/ingest "PROV1" (dc/collection {:organizations [(dc/org :archive-center "Larc")]}))

        coll5 (d/ingest "PROV2" (dc/collection {:organizations [(dc/org :archive-center "SEDAC AC")
                                                                (dc/org :processing-center "SEDAC PC")]}))
        coll6 (d/ingest "PROV2" (dc/collection {:organizations [(dc/org :archive-center "Larc")]}))

        coll7 (d/ingest "PROV2" (dc/collection {:organizations [(dc/org :archive-center "Sedac AC")
                                                                (dc/org :processing-center "Sedac")]}))]

    (index/refresh-elastic-index)

    (testing "search coll by archive center"
      (are [org-name items] (d/refs-match? items (search/find-refs :collection {:archive-center org-name}))
           "Larc" [coll4 coll6]
           "SEDAC AC" [coll5 coll7]
           "SEDAC PC" []
           "Sedac AC" [coll5 coll7]
           "BLAH" []))
    (testing "case sensitivity ..."
      (are [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
           {:archive-center "Sedac AC", "options[archive-center][ignore-case]" "false"} [coll7]
           {:archive-center "sedac ac", "options[archive-center][ignore-case]" "true"} [coll5 coll7]))
    (testing "search using wild cards"
      (is (d/refs-match? [coll5 coll7]
                         (search/find-refs :collection {:archive-center "S*", "options[archive-center][pattern]" "true"}))))
    (testing "search using AND/OR operators"
      (are [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
           {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"]} [coll4 coll5 coll6 coll7]))

    (testing "search collections by archive center with AQL."
      (are [items centers options]
           (let [condition (merge {:archiveCenter centers} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection [condition])))
           [coll4 coll6] "Larc" {}
           [coll5] "SEDAC AC" {}
           [coll7] "Sedac AC" {}
           [] "sedac ac" {}
           [] "SEDAC PC" {}
           [] "BLAH" {}
           [coll4 coll5 coll6] ["SEDAC AC" "Larc"] {}

           ;; Wildcards
           [coll5 coll7] "S%" {:pattern true}
           [coll5] "SEDAC _C" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [coll5 coll7] "sedac ac" {:ignore-case true}
           [] "sedac ac" {:ignore-case false}))))

