(ns cmr.access-control.test.services.permitted-concept-id-search
  "Contains unit tests for permitted-concept-id-search namespace"
  (:require
    [clojure.test :refer :all]
    [cmr.access-control.services.permitted-concept-id-search :as pcs]
    [cmr.access-control.int-test.fixtures :as fixtures]
    [cmr.access-control.test.util :as u]
    [cmr.common-app.services.search.group-query-conditions :as gc]
    [cmr.common-app.services.search.query-model :as common-qm]
    [cmr.transmit.metadata-db2 :as mdb2]))

(use-fixtures :each
  (fixtures/reset-fixture {"prov1guid" "PROV1"}))
(use-fixtures :once (fixtures/int-test-fixtures))

(deftest get-permitted-concept-id-conditions
  (let [concept-id-access-value-1 (u/save-collection {:entry-title (str "coll1" " entry title")
                                                      :short-name "coll1"
                                                      :native-id "coll1"
                                                      :provider-id "PROV1"
                                                      :access-value 1})
        concept-id-access-value-nil (u/save-collection {:entry-title (str "coll2" " entry title")
                                                        :short-name "coll2"
                                                        :native-id "coll2"
                                                        :provider-id "PROV1"
                                                        :access-value nil})
        concept-access-value-1 (mdb2/get-latest-concept (u/conn-context) concept-id-access-value-1)
        concept-access-value-nil (mdb2/get-latest-concept (u/conn-context) concept-id-access-value-nil)]
    (testing "create permitted-concept-id conditions with access value 1"
      (is (= (pcs/get-permitted-concept-id-conditions (u/conn-context) concept-access-value-1)
             (gc/group-conds
              :or
              [(gc/group-conds
                 :and
                 [(common-qm/boolean-condition :collection-identifier false)
                  (common-qm/boolean-condition :collection-applicable true)])
               (gc/group-conds
                 :and
                 [(common-qm/numeric-range-intersection-condition
                    :collection-access-value-min
                    :collection-access-value-max
                    1.0
                    1.0)
                  (common-qm/boolean-condition :collection-applicable true)])]))))
    (testing "create permitted-concept-id conditions with access value nil"
      (is (= (pcs/get-permitted-concept-id-conditions (u/conn-context) concept-access-value-nil)
             (gc/group-conds
              :or
              [(gc/group-conds
                 :and
                 [(common-qm/boolean-condition :collection-identifier false)
                  (common-qm/boolean-condition :collection-applicable true)])
               (gc/group-conds
                 :and
                 [(common-qm/boolean-condition :collection-access-value-include-undefined-value true)
                  (common-qm/boolean-condition :collection-applicable true)])]))))))
