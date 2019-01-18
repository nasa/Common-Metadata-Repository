(ns cmr.system-int-test.search.coll-archive-ctr-search-test
  "Search CMR Collections by Data Centers."
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as util :refer [are3]]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.data2.umm-spec-common :as data-umm-cmn]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-colls-by-archive-center-names
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 2 {:DataCenters []}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 3 {:DataCenters [(data-umm-cmn/data-center
                                                                                              {:Roles ["PROCESSOR"]
                                                                                               :ShortName "Larc"})]}))
        coll4 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 4 {:DataCenters [(data-umm-cmn/data-center
                                                                                              {:Roles ["ARCHIVER"]
                                                                                               :ShortName "Larc"})]}))

        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection 5 {:DataCenters [(data-umm-cmn/data-center
                                                                                              {:Roles ["ARCHIVER"]
                                                                                               :ShortName "SEDAC AC"})
                                                                                            (data-umm-cmn/data-center {:Roles ["PROCESSOR"]
                                                                                                                       :ShortName "SEDAC PC"})]}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection 6 {:DataCenters [(data-umm-cmn/data-center
                                                                                              {:Roles ["ARCHIVER"]
                                                                                               :ShortName "Larc"})]}))
        coll7 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection 7 {:DataCenters [(data-umm-cmn/data-center
                                                                                              {:Roles ["ARCHIVER"]
                                                                                               :ShortName "Sedac AC"})
                                                                                            (data-umm-cmn/data-center {:Roles ["PROCESSOR"]
                                                                                                                       :ShortName "Sedac"})]}))
        ;; KMS collections, but with different case for the short-names
        kms-coll1 (d/ingest-umm-spec-collection "PROV1"
                            (data-umm-c/collection 8
                              {:DataCenters [(data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                                                        :ShortName "Ucar/ncar/eoL/ceoPdm"})]}))
        kms-coll2 (d/ingest-umm-spec-collection "PROV1"
                            (data-umm-c/collection 9
                              {:DataCenters [(data-umm-cmn/data-center {:Roles ["ARCHIVER"]
                                                                        :ShortName "Doi/uSGs/Cmg/wHSc"})]}))
        ;; DIF collections support distribution-center data centers
        dif-coll1 (d/ingest-umm-spec-collection "PROV1"
                            (data-umm-c/collection 10
                              {:DataCenters [(data-umm-cmn/data-center {:Roles ["DISTRIBUTOR"]
                                                                        :ShortName "Dist center"})]})
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

    (testing "search coll by data center"
      (are [org-name items] (d/refs-match? items (search/find-refs :collection {:data-center org-name}))
           "Larc" [coll3 coll4 coll6]
           "SEDAC AC" [coll5 coll7]
           "SEDAC PC" [coll5]
           "Sedac AC" [coll5 coll7]
           "Dist center" [dif-coll1]
           "BLAH" []))
    (testing "data center case sensitivity ..."
      (are [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
           {:data-center "Sedac AC", "options[data-center][ignore-case]" "false"} [coll7]
           {:data-center "sedac ac", "options[data-center][ignore-case]" "true"} [coll5 coll7]))
    (testing "search data center using wild cards"
      (is (d/refs-match? [coll5 coll7]
                         (search/find-refs :collection {:data-center "S*", "options[data-center][pattern]" "true"}))))

    (doseq [field [:data-center :data-center-h]]
      (testing (str "search" (name field) "using AND/OR operators")
        (are3 [kvs items] (d/refs-match? items (search/find-refs :collection kvs))
              "search for collections containing any of the data centers- default case"
              {(str (name field) "[]") ["SEDAC AC" "Larc" "Sedac AC"]} [coll3 coll4 coll5 coll6 coll7]

              "search for collections containing all of the data centers"
              {(str (name field) "[]") ["SEDAC AC" "SEDAC PC"] (str "options[" (name field) "][and]") "true"} [coll5])))

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
           [coll5] {:and [{:data_center {:short_name "SEDAC AC"}} {:data_center {:short_name "SEDAC PC"}}]}
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
