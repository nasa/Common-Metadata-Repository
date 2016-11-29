(ns cmr.access-control.test.services.permitted-concept-id-search
  "Contains unit tests for permitted-concept-id-search namespace"
  (:require
    [clojure.test :refer :all]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.services.permitted-concept-id-search :as pcs]
    [cmr.access-control.test.util :as u]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.transmit.metadata-db2 :as mdb2]
    [cmr.umm-spec.time :as spec-time]
    [cmr.umm-spec.umm-spec-core :as umm-spec]
    [cmr.umm.start-end-date :as umm-lib-time]
    [cmr.umm.umm-core :as umm-lib]))

(use-fixtures :each
  (fixtures/reset-fixture {"prov1guid" "PROV1"}))
(use-fixtures :once (fixtures/int-test-fixtures))

(deftest get-permitted-concept-id-conditions
  (let [collection-concept-id-access-value-1 (u/save-collection {:entry-title (str "coll1" " entry title")
                                                                 :short-name "coll1"
                                                                 :native-id "coll1"
                                                                 :provider-id "PROV1"
                                                                 :access-value 1})
        collection-concept-id-access-value-nil (u/save-collection {:entry-title (str "coll2" " entry title")
                                                                   :short-name "coll2"
                                                                   :native-id "coll2"
                                                                   :provider-id "PROV1"
                                                                   :access-value nil})

        collection-concept-access-value-1 (mdb2/get-latest-concept (u/conn-context) collection-concept-id-access-value-1)
        collection-concept-access-value-nil (mdb2/get-latest-concept (u/conn-context) collection-concept-id-access-value-nil)
        collection-parsed-1 (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} (u/conn-context)) collection-concept-access-value-1)
        collection-parsed-nil (umm-spec/parse-metadata (merge {:ignore-kms-keywords true} (u/conn-context)) collection-concept-access-value-nil)
        collection-start-date-1 (spec-time/collection-start-date collection-parsed-1)
        collection-stop-date-1 (spec-time/collection-end-date collection-parsed-1)
        collection-start-date-nil (spec-time/collection-start-date collection-parsed-nil)
        collection-stop-date-nil (spec-time/collection-end-date collection-parsed-nil)

        granule-concept-id-access-value-1 (u/save-granule collection-concept-id-access-value-1 {:access-value 1})
        granule-concept-id-access-value-nil (u/save-granule collection-concept-id-access-value-nil)
        granule-concept-access-value-1 (mdb2/get-latest-concept (u/conn-context) granule-concept-id-access-value-1)
        granule-concept-access-value-nil (mdb2/get-latest-concept (u/conn-context) granule-concept-id-access-value-nil)
        granule-parsed-1 (umm-lib/parse-concept granule-concept-access-value-1)
        granule-parsed-nil (umm-lib/parse-concept granule-concept-access-value-nil)
        granule-start-date-1 (umm-lib-time/start-date :granule (:temporal granule-parsed-1))
        granule-stop-date-1 (umm-lib-time/end-date :granule (:temporal granule-parsed-1))
        granule-start-date-nil (umm-lib-time/start-date :granule (:temporal granule-parsed-nil))
        granule-stop-date-nil (umm-lib-time/end-date :granule (:temporal granule-parsed-nil))]
    (testing "collection create permitted-concept-id conditions with access value 1"
      (is (= (gc/and-conds
               [(common-qm/string-condition :provider "PROV1" false false)
                (gc/or-conds
                  [(gc/and-conds
                     [(common-qm/boolean-condition :collection-applicable true)
                      (common-qm/boolean-condition :collection-identifier false)])
                   (gc/and-conds
                     [(common-qm/numeric-range-intersection-condition
                        :access-value-min
                        :access-value-max
                        1.0
                        1.0)
                      (common-qm/boolean-condition :collection-applicable true)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :collection-applicable true)
                      (gc/or-conds
                        [(gc/and-conds
                           [(common-qm/string-condition :temporal-mask "contains" true false)
                            (common-qm/date-range-condition :temporal-range-start-date nil collection-start-date-1)
                            (common-qm/date-range-condition :temporal-range-stop-date collection-stop-date-1 nil)])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "intersect" true false)
                            (gc/or-conds
                              [(gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil collection-stop-date-1)
                                  (common-qm/date-range-condition :temporal-range-start-date collection-start-date-1 nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date collection-start-date-1 nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date nil collection-stop-date-1)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil collection-start-date-1)
                                  (common-qm/date-range-condition :temporal-range-stop-date collection-stop-date-1 nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date nil collection-stop-date-1)
                                  (common-qm/date-range-condition :temporal-range-start-date collection-start-date-1 nil)])])])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "disjoint" true false)
                            (gc/or-conds
                              [(common-qm/date-range-condition :temporal-range-start-date collection-stop-date-1 nil true)
                               (common-qm/date-range-condition :temporal-range-stop-date nil collection-start-date-1 true)])])])])])])
             (pcs/get-permitted-concept-id-conditions (u/conn-context) collection-concept-access-value-1))))
    (testing "collection create permitted-concept-id conditions with access value nil"
      (is (= (gc/and-conds
               [(common-qm/string-condition :provider "PROV1" false false)
                (gc/or-conds
                  [(gc/and-conds
                     [(common-qm/boolean-condition :collection-applicable true)
                      (common-qm/boolean-condition :collection-identifier false)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :access-value-include-undefined-value true)
                      (common-qm/boolean-condition :collection-applicable true)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :collection-applicable true)
                      (gc/or-conds
                        [(gc/and-conds
                           [(common-qm/string-condition :temporal-mask "contains" true false)
                            (common-qm/date-range-condition :temporal-range-start-date nil collection-start-date-nil)
                            (common-qm/date-range-condition :temporal-range-stop-date collection-stop-date-nil nil)])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "intersect" true false)
                            (gc/or-conds
                              [(gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil collection-stop-date-nil)
                                  (common-qm/date-range-condition :temporal-range-start-date collection-start-date-nil nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date collection-start-date-nil nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date nil collection-stop-date-nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil collection-start-date-nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date collection-stop-date-nil nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date nil collection-stop-date-nil)
                                  (common-qm/date-range-condition :temporal-range-start-date collection-start-date-nil nil)])])])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "disjoint" true false)
                            (gc/or-conds
                              [(common-qm/date-range-condition :temporal-range-start-date collection-stop-date-nil nil true)
                               (common-qm/date-range-condition :temporal-range-stop-date nil collection-start-date-nil true)])])])])])])
             (pcs/get-permitted-concept-id-conditions (u/conn-context) collection-concept-access-value-nil))))
    (testing "granule create permitted-concept-id conditions with access value 1"
      (is (= (gc/and-conds
               [(common-qm/string-condition :provider "PROV1" false false)
                (gc/or-conds
                  [(gc/and-conds
                     [(common-qm/boolean-condition :granule-applicable true)
                      (common-qm/boolean-condition :granule-identifier false)])
                   (gc/and-conds
                     [(common-qm/numeric-range-intersection-condition
                        :access-value-min
                        :access-value-max
                        1.0
                        1.0)
                      (common-qm/boolean-condition :granule-applicable true)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :granule-applicable true)
                      (gc/or-conds
                        [(gc/and-conds
                           [(common-qm/string-condition :temporal-mask "contains" true false)
                            (common-qm/date-range-condition :temporal-range-start-date nil granule-start-date-1)
                            (common-qm/date-range-condition :temporal-range-stop-date granule-stop-date-1 nil)])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "intersect" true false)
                            (gc/or-conds
                              [(gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil granule-stop-date-1)
                                  (common-qm/date-range-condition :temporal-range-start-date granule-start-date-1 nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date granule-start-date-1 nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date nil granule-stop-date-1)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil granule-start-date-1)
                                  (common-qm/date-range-condition :temporal-range-stop-date granule-stop-date-1 nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date nil granule-stop-date-1)
                                  (common-qm/date-range-condition :temporal-range-start-date granule-start-date-1 nil)])])])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "disjoint" true false)
                            (gc/or-conds
                              [(common-qm/date-range-condition :temporal-range-start-date granule-stop-date-1 nil true)
                               (common-qm/date-range-condition :temporal-range-stop-date nil granule-start-date-1 true)])])])])])])
             (pcs/get-permitted-concept-id-conditions (u/conn-context) granule-concept-access-value-1))))
    (testing "granule create permitted-concept-id conditions with access value nil"
      (is (= (gc/and-conds
               [(common-qm/string-condition :provider "PROV1" false false)
                (gc/or-conds
                  [(gc/and-conds
                     [(common-qm/boolean-condition :granule-applicable true)
                      (common-qm/boolean-condition :granule-identifier false)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :access-value-include-undefined-value true)
                      (common-qm/boolean-condition :granule-applicable true)])
                   (gc/and-conds
                     [(common-qm/boolean-condition :granule-applicable true)
                      (gc/or-conds
                        [(gc/and-conds
                           [(common-qm/string-condition :temporal-mask "contains" true false)
                            (common-qm/date-range-condition :temporal-range-start-date nil granule-start-date-nil)
                            (common-qm/date-range-condition :temporal-range-stop-date granule-stop-date-nil nil)])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "intersect" true false)
                            (gc/or-conds
                              [(gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil granule-stop-date-nil)
                                  (common-qm/date-range-condition :temporal-range-start-date granule-start-date-nil nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date granule-start-date-nil nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date nil granule-stop-date-nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-start-date nil granule-start-date-nil)
                                  (common-qm/date-range-condition :temporal-range-stop-date granule-stop-date-nil nil)])
                               (gc/and-conds
                                 [(common-qm/date-range-condition :temporal-range-stop-date nil granule-stop-date-nil)
                                  (common-qm/date-range-condition :temporal-range-start-date granule-start-date-nil nil)])])])
                         (gc/and-conds
                           [(common-qm/string-condition :temporal-mask "disjoint" true false)
                            (gc/or-conds
                              [(common-qm/date-range-condition :temporal-range-start-date granule-stop-date-nil nil true)
                               (common-qm/date-range-condition :temporal-range-stop-date nil granule-start-date-nil true)])])])])])])
             (pcs/get-permitted-concept-id-conditions (u/conn-context) granule-concept-access-value-nil))))))
