(ns cmr.system-int-test.search.collection-provider-short-name-search-test
  "Integration tests for searching collections with provider short names."
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :refer [are2]]
   [cmr.system-int-test.data2.core :as d]
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
  (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection 1 {}))
        coll2 (d/ingest-umm-spec-collection "PROV2" (data-umm-c/collection 2 {}))
        coll3 (d/ingest-umm-spec-collection "PROV3" (data-umm-c/collection 3 {}))
        coll4 (d/ingest-umm-spec-collection "PROV4" (data-umm-c/collection 4 {}))
        coll5 (d/ingest-umm-spec-collection "PROV5" (data-umm-c/collection 5 {}))]
    (index/wait-until-indexed)

    (testing "regular searches"
      (are2
        [expected provider-short-names options]
        (d/refs-match?
          expected
          (search/find-refs :collection
                            (merge {:provider-short-name provider-short-names} options)))

        "exact match, ignore-case by default"
        [coll1 coll2]
        ["Provider 1"] {}

        "exact match, string value"
        [coll1 coll2]
        "Provider 1" {}

        "exact match, special chars"
        [coll3]
        ["Test provider with special chars :) ? * \\ [] {} ☺, etc."] {}

        "ignore case true"
        [coll1 coll2]
        ["Provider 1"] {"options[provider-short-name][ignore-case]" "true"}

        "ignore case false"
        [coll1]
        ["Provider 1"] {"options[provider-short-name][ignore-case]" "false"}

        "multiple providers"
        [coll1 coll2 coll4]
        ["Provider 1" "Not important"] {}

        "no match"
        []
        "NoMatch" {}))

    (testing "search combined with provider-id"
      (are2
        [expected provider-id provider-short-names options]
        (d/refs-match?
          expected
          (search/find-refs :collection
                            (merge {:provider provider-id
                                    :provider-short-name provider-short-names} options)))

        "combined with provider-id search, overlap"
        [coll1 coll2]
        "PROV1" ["Provider 1"] {}

        "combined with provider-id search, extra"
        [coll1 coll2 coll4]
        "PROV4" ["Provider 1"] {}

        "combined with provider-id search, no match on provider-short-name"
        [coll4]
        "PROV4" ["NoMatch"] {}))

    (testing "search after a provider update"
      ;; find the collection before provider update
      (is (d/refs-match?
            [coll5]
            (search/find-refs :collection
                              {:provider-short-name "WillbeUpdated"} {})))
      ;; update the provider
      (ingest/update-ingest-provider {:provider-id "PROV5"
                                      :short-name "Updated PROV5"
                                      :cmr-only true
                                      :small false})
      ;; not finding collection on previous provider-short-name
      (is (d/refs-match?
            []
            (search/find-refs :collection
                              {:provider-short-name "WillbeUpdated"} {})))
      ;; find the collection on updated provider-short-name
      (is (d/refs-match?
            [coll5]
            (search/find-refs :collection
                              {:provider-short-name "Updated PROV5"} {}))))))

(deftest search-collections-by-provider-short-name-error-cases
  (are2
    [provider-short-names options expected-errors]
    (= {:status 400 :errors expected-errors}
       (search/find-refs :collection
                         (merge {:provider-short-name provider-short-names} options)))

    "search with invalid value - map, not string or list"
    {:x "provider 1"} {}
    ["Parameter [provider_short_name] must have a single value or multiple values."]

    "search with invalid options, pattern is not allowed"
    "provider 1" {"options[provider-short-name][pattern]" "true"}
    ["Parameter [provider_short_name] does not support search by pattern."]))
