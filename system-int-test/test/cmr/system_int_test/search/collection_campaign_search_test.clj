(ns cmr.system-int-test.search.collection-campaign-search-test
  "Integration test for CMR collection search by campaign terms"
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

(deftest search-by-campaign-short-names
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "C1" :ShortName "S1"}))
        coll2 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Projects [] :EntryTitle "C2" :ShortName "S2"}))
        coll3 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:Projects (data-umm-cmn/projects "ESI") :EntryTitle "C3" :ShortName "S3"}))

        coll4 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Projects (data-umm-cmn/projects "ESI" "Esi") :EntryTitle "C4" :ShortName "S4"}))
        coll5 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Projects (data-umm-cmn/projects "EVI" "EPI") :EntryTitle "C5" :ShortName "S5"}))
        coll6 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection {:Projects (data-umm-cmn/projects "ESI" "EVI" "EPI") :EntryTitle "C6" :ShortName "S6"}))]

    (index/wait-until-indexed)

    (testing "search by single campaign term."
      (are [campaign-sn items] (d/refs-match? items (search/find-refs :collection {:campaign campaign-sn}))
           "ESI" [coll3 coll4 coll6]
           "EVI" [coll5 coll6]
           "EPI" [coll5 coll6]
           "Esi" [coll3 coll4 coll6]
           "BLAH" []))
    (testing "search using redundant campaign sn terms."
      (are [campaign-kv items] (d/refs-match? items (search/find-refs :collection campaign-kv))
           {:campaign "ESI"} [coll3 coll4 coll6]
           {"campaign[]" ["ESI", "ESI"]} [coll3 coll4 coll6]))
    (testing "case sensitivity ..."
      (are [campaign-kvs items] (d/refs-match? items (search/find-refs :collection campaign-kvs))
           {:campaign "EpI", "options[campaign][ignore-case]" "false"} []
           {:campaign "EPI"} [coll5 coll6]
           {:campaign "EpI", "options[campaign][ignore-case]" "true"} [coll5 coll6]))
    (testing "search by unique campaign sn terms to get max collections"
      (is (d/refs-match? [coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {"campaign[]" ["ESI" "EVI" "EPI" "Esi"]}))))
    (testing "search by wild card to get max collections"
      (is (d/refs-match? [coll3 coll4 coll5 coll6]
                         (search/find-refs :collection {:campaign "E*", "options[campaign][pattern]" "true"}))))

    (doseq [field [:campaign :project-h]]
      (testing (str "search by " (name field) "sn terms ORed")
        (are3 [campaign-kvs items] (d/refs-match? items (search/find-refs :collection campaign-kvs))
              "searching for collections containing all the values"
              {(str (name field) "[]") ["ESI" "EPI" "EVI"], (str "options[" (name field) "][and]") "true"} [coll6]

              "searching for collections containg either of the values"
              {(str (name field) "[]") ["ESI" "EPI" "EVI"], (str "options[" (name field) "][and]") "false"} [coll3 coll4 coll5 coll6]

              "searching for collections containing any of the values default case"
              {(str (name field) "[]") ["ESI" "EPI" "EVI"]} [coll3 coll4 coll5 coll6])))

    (testing "search campaign by AQL."
      (are [items campaigns options]
           (let [condition (merge {:CampaignShortName campaigns} options)]
             (d/refs-match? items
                            (search/find-refs-with-aql :collection [condition])))
           [coll3 coll4 coll6] "ESI" {}
           [coll5 coll6] "EVI" {}
           [coll5 coll6] "EPI" {}
           [coll4] "Esi" {}
           [] "BLAH" {}

           ;; Multiple values
           [coll3 coll4 coll5 coll6] ["ESI" "EVI"] {}

           ;; Wildcards
           [coll3 coll4 coll5 coll6] "E%" {:pattern true}
           [] "E%" {:pattern false}
           [] "E%" {}
           [coll3 coll4 coll5 coll6] "%I" {:pattern true}
           [coll5 coll6] "EP_" {:pattern true}
           [coll3 coll4 coll5 coll6] "E_%" {:pattern true}
           [] "%Q%" {:pattern true}

           ;; Ignore case
           [] "epi" {}
           [coll5 coll6] "epi" {:ignore-case true}
           [] "epi" {:ignore-case false}))

    (testing "Search by project/campaign using JSON query."
      (are [items search]
           (d/refs-match? items (search/find-refs-with-json-query :collection {} search))

           [coll3 coll4 coll6] {:project "ESI"}
           [coll5 coll6] {:project "EVI"}
           [coll5 coll6] {:project "EPI"}
           [] {:project "BLAH"}

           ;; Multiple values
           [coll3 coll4 coll5 coll6] {:or [{:project "ESI"} {:project "EVI"}]}

           ;; Wildcards
           [coll3 coll4 coll5 coll6] {:project {:value "E*" :pattern true}}
           [] {:project {:value "E*" :pattern false}}
           [] {:project {:value "E*"}}
           [coll3 coll4 coll5 coll6] {:project {:value "*I" :pattern true}}
           [coll5 coll6] {:project {:value "EP?" :pattern true}}
           [coll3 coll4 coll5 coll6] {:project {:value "E?*" :pattern true}}
           [] {:project {:value "*Q*" :pattern true}}

           ;; Ignore case
           [coll5 coll6] {:project {:value "epi"}}
           [coll5 coll6] {:project {:value "epi" :ignore_case true}}
           [] {:project {:value "epi" :ignore_case false}}))))
