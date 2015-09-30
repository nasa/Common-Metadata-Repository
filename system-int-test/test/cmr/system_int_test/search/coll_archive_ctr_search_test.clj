(ns ^{:doc "Search CMR Collections by Data Centers."}
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
                                                                (dc/org :processing-center "Sedac")]}))
        ;; KMS collections, but with different case for the short-names
        kms-coll1 (d/ingest "PROV1"
                            (dc/collection
                              {:organizations [(dc/org :archive-center "Ucar/ncar/eoL/ceoPdm")]}))
        kms-coll2 (d/ingest "PROV1"
                            (dc/collection
                              {:organizations [(dc/org :archive-center "Doi/uSGs/Cmg/wHSc")]}))
        ;; DIF collections support distribution-center data centers
        dif-coll1 (d/ingest "PROV1"
                            (dc/collection-dif
                              {:organizations [(dc/org :distribution-center "Dist center")]})
                            {:format :dif})]

    (index/wait-until-indexed)

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
           [] "sedac ac" {:ignore-case false}))

    (testing "Search collections by archive center using JSON Query."
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll4 coll6] {:archive_center {:short_name "Larc"}}
           [coll5 coll7] {:archive_center {:short_name "SEDAC AC"}}
           [coll5 coll7] {:archive_center {:short_name "Sedac AC"}}
           [] {:archive_center {:short_name "SEDAC PC"}}
           [] {:archive_center {:short_name "BLAH"}}
           [coll4 coll5 coll6 coll7] {:or [{:archive_center {:short_name "SEDAC AC"}} {:archive_center {:short_name "Larc"}}]}
           [] {:and [{:archive_center {:short_name "SEDAC AC"}} {:archive_center {:short_name "Larc"}}]}
           [coll1 coll2 coll3 coll4 coll6 kms-coll1 kms-coll2 dif-coll1] {:not {:archive_center {:short_name "SEDAC AC"}}}

           ;; Wildcards
           [coll5 coll7] {:archive_center {:short_name "S*" :pattern true}}
           [coll5 coll7] {:archive_center {:short_name "SEDAC ?C" :pattern true}}
           [coll5] {:archive_center {:short_name "SEDAC ?C" :pattern true :ignore_case false}}
           [] {:archive_center {:short_name "*Q*" :pattern true}}

           ;; Ignore case
           [coll5 coll7] {:archive_center {:short_name "sedac ac" :ignore_case true}}
           [] {:archive_center {:short_name "sedac ac" :ignore_case false}}

           ;; Test searching on KMS subfields
           [kms-coll1 kms-coll2] {:archive_center {:level_0 "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES"
                                           :ignore_case false}}
           [] {:archive_center {:level_0 "government agencies-u.s. federal agencies" :ignore_case false}}
           [kms-coll1 kms-coll2] {:archive_center {:level_0 "government agencies-u.s. federal agencies"
                                           :ignore_case true}}
           [kms-coll1] {:archive_center {:level_1 "NSF"}}
           [kms-coll2] {:archive_center {:level_2 "USGS"}}
           [kms-coll2] {:archive_center {:level_3 "Added level 3 value"}}

           ;; Short name uses KMS case rather than metadata case
           [] {:archive_center {:short_name "Ucar/ncar/eoL/ceoPdm" :ignore_case false}}
           [kms-coll1] {:archive_center {:short_name "Ucar/ncar/eoL/ceoPdm" :ignore_case true}}
           [kms-coll1] {:archive_center {:short_name "UCAR/NCAR/EOL/CEOPDM" :ignore_case false}}

           [kms-coll1] {:archive_center {:long_name "ceop data M?nagement*" :pattern true}}
           [] {:archive_center {:long_name "ceop data M?nagement*"}}
           [kms-coll1] {:archive_center {:url "http://www.eol.ucar.edu/projects/ceop/dm/"}}
           [kms-coll2] {:archive_center {:uuid "69db99c6-54d6-40b9-9f72-47eab9c34869"}}
           [kms-coll2] {:archive_center {:any "69db99c6*" :pattern true}}))

    (testing "Search collections by data center using JSON Query."
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           ;; Finds processing centers
           [coll3 coll4 coll6] {:data_center {:short_name "Larc"}}
           [coll5] {:data_center {:short_name "SEDAC PC"}}
           [coll3 coll4 coll5 coll6 coll7] {:or [{:data_center {:short_name "SEDAC AC"}} {:data_center {:short_name "Larc"}}]}

           ;; Finds distribution centers
           [dif-coll1] {:data_center {:short_name "Dist center"}}

           [coll5 coll7] {:data_center {:short_name "SEDAC AC"}}
           [coll5 coll7] {:data_center {:short_name "Sedac AC"}}
           [] {:data_center {:short_name "BLAH"}}
           [] {:and [{:data_center {:short_name "SEDAC AC"}} {:data_center {:short_name "Larc"}}]}
           [coll1 coll2 coll3 coll4 coll6 kms-coll1 kms-coll2 dif-coll1] {:not {:data_center {:short_name "SEDAC AC"}}}

           ;; Wildcards
           [coll5 coll7] {:data_center {:short_name "S*" :pattern true}}
           [coll5 coll7] {:data_center {:short_name "SEDAC ?C" :pattern true}}
           [coll5] {:data_center {:short_name "SEDAC ?C" :pattern true :ignore_case false}}
           [] {:data_center {:short_name "*Q*" :pattern true}}

           ;; Ignore case
           [coll5 coll7] {:data_center {:short_name "sedac ac" :ignore_case true}}
           [] {:data_center {:short_name "sedac ac" :ignore_case false}}

           ;; Test searching on KMS subfields
           [kms-coll1 kms-coll2] {:data_center {:level_0 "GOVERNMENT AGENCIES-U.S. FEDERAL AGENCIES"
                                           :ignore_case false}}
           [] {:data_center {:level_0 "government agencies-u.s. federal agencies" :ignore_case false}}
           [kms-coll1 kms-coll2] {:data_center {:level_0 "government agencies-u.s. federal agencies"
                                           :ignore_case true}}
           [kms-coll1] {:data_center {:level_1 "NSF"}}
           [kms-coll2] {:data_center {:level_2 "USGS"}}
           [kms-coll2] {:data_center {:level_3 "Added level 3 value"}}

           ;; Short name uses KMS case rather than metadata case
           [] {:data_center {:short_name "Ucar/ncar/eoL/ceoPdm" :ignore_case false}}
           [kms-coll1] {:data_center {:short_name "Ucar/ncar/eoL/ceoPdm" :ignore_case true}}
           [kms-coll1] {:data_center {:short_name "UCAR/NCAR/EOL/CEOPDM" :ignore_case false}}

           [kms-coll1] {:data_center {:long_name "ceop data M?nagement*" :pattern true}}
           [] {:data_center {:long_name "ceop data M?nagement*"}}
           [kms-coll1] {:data_center {:url "http://www.eol.ucar.edu/projects/ceop/dm/"}}
           [kms-coll2] {:data_center {:uuid "69db99c6-54d6-40b9-9f72-47eab9c34869"}}
           [kms-coll2] {:data_center {:any "69db99c6*" :pattern true}}))))


