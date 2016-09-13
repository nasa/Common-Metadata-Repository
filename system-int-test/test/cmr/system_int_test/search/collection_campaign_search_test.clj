(ns cmr.system-int-test.search.collection-campaign-search-test
  "Integration test for CMR collection search by campaign terms"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest search-by-campaign-short-names
  (let [coll1 (d/ingest "PROV1" (dc/collection {}))
        coll2 (d/ingest "PROV1" (dc/collection {:projects []}))
        coll3 (d/ingest "PROV1" (dc/collection {:projects (dc/projects "ESI")}))

        coll4 (d/ingest "PROV2" (dc/collection {:projects (dc/projects "ESI" "Esi")}))
        coll5 (d/ingest "PROV2" (dc/collection {:projects (dc/projects "EVI" "EPI")}))
        coll6 (d/ingest "PROV2" (dc/collection {:projects (dc/projects "ESI" "EVI" "EPI")}))]

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
    (testing "search by campaign sn terms ORed"
      (are [campaign-kvs items] (d/refs-match? items (search/find-refs :collection campaign-kvs))
           {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "true"} [coll6]
           {"campaign[]" ["ESI" "EPI" "EVI"], "options[campaign][and]" "false"} [coll3 coll4 coll5 coll6]
           {"campaign[]" ["ESI" "EPI" "EVI"]} [coll3 coll4 coll5 coll6]))

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
