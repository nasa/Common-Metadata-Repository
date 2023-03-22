(ns cmr.system-int-test.search.collection-provider-short-name-search-test
  "Integration tests for searching collections with provider short names."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are3]]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.search-util :as search]))

(use-fixtures :each (ingest/reset-fixture
                      [{:provider-guid "provguid1"
                        :provider-id "PROV1"
                        :short-name "Provider 1"}
                       {:provider-guid "provguid2"
                        :provider-id "PROV2"
                        :short-name "PROVIDER 1"}
                       {:provider-guid "provguid3"
                        :provider-id "PROV3"
                        :short-name "Test provider with special chars :) ? * \\ [] {} ☺, etc."}
                       {:provider-guid "provguid4"
                        :provider-id "PROV4"
                        :short-name "Not important"}
                       {:provider-guid "provguid5"
                        :provider-id "PROV5"
                        :short-name "WillbeUpdated"}]))

(deftest search-collections-by-provider-short-name
  (let [coll1 (data-core/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {}))
        coll2 (data-core/ingest-umm-spec-collection "PROV2" (data-umm-c/collection 2 {}))
        coll3 (data-core/ingest-umm-spec-collection "PROV3" (data-umm-c/collection 3 {}))
        coll4 (data-core/ingest-umm-spec-collection "PROV4" (data-umm-c/collection 4 {}))
        coll5 (data-core/ingest-umm-spec-collection "PROV5" (data-umm-c/collection 5 {}))]
    (index/wait-until-indexed)

    (testing "regular searches"
      (are3
        [expected provider-short-names options]
        (data-core/refs-match?
          expected
          (search/find-refs :collection
                            (merge {:provider-short-name provider-short-names} options)))

        "exact match, ignore-case by default"
        [coll1]
        ["PROV1"] {}

        "exact match, string value"
        [coll1]
        "PROV1" {}

        "ignore case true"
        [coll1]
        ["prov1"] {"options[provider-short-name][ignore-case]" "true"}

        "ignore case false"
        [coll1]
        ["PROV1"] {"options[provider-short-name][ignore-case]" "false"}

        "multiple providers"
        [coll1 coll2 coll4]
        ["PROV1" "PROV2" "PROV4"] {}

        "no match"
        []
        "NoMatch" {}))

    (testing "search combined with provider-id"
      (are3
        [expected provider-id provider-short-names options]
        (data-core/refs-match?
          expected
          (search/find-refs :collection
                            (merge {:provider provider-id
                                    :provider-short-name provider-short-names} options)))

        "combined with provider-id search, overlap"
        [coll1 coll2]
        "PROV1" ["PROV2"] {}

        "combined with provider-id search, extra"
        [coll2 coll4]
        "PROV4" ["PROV2" "PROV4"] {}

        "combined with provider-id search, no match on provider-short-name"
        [coll4]
        "PROV4" ["NoMatch"] {}))))

(deftest search-collections-by-provider-short-name-error-cases
  (are3
    [provider-short-names options expected-errors]
    (is (= {:status 400 :errors expected-errors}
       (search/find-refs :collection
                         (merge {:provider-short-name provider-short-names} options))))

    "search with invalid value - map, not string or list"
    {:x "provider 1"} {}
    ["Parameter [provider_short_name] must have a single value or multiple values."]

    "search with invalid options, pattern is not allowed"
    "provider 1" {"options[provider-short-name][pattern]" "true"}
    ["Parameter [provider_short_name] does not support search by pattern."]))
