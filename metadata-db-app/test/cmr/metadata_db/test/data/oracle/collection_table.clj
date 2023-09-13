(ns cmr.metadata-db.test.data.oracle.collection-table
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.data.oracle.collection-table :as ct]
    [cmr.metadata-db.test.test-utils :as tu]))

(deftest collection-constraint-sql-false-test
  (testing "valid table name"
    (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
        (are [table-name query] (= query (tu/remove-spaces-and-new-lines (ct/collection-constraint-sql non-small-provider table-name)))
                                "table_name" "CONSTRAINTtable_name_pkPRIMARYKEY(id),CONSTRAINTtable_name_con_revUNIQUE(native_id,revision_id)USINGINDEX(createuniqueindextable_name_ucr_iONtable_name(native_id,revision_id)),CONSTRAINTtable_name_cid_revUNIQUE(concept_id,revision_id)USINGINDEX(createuniqueindextable_name_criONtable_name(concept_id,revision_id))",
                                "table_123_valid" "CONSTRAINTtable_123_valid_pkPRIMARYKEY(id),CONSTRAINTtable_123_valid_con_revUNIQUE(native_id,revision_id)USINGINDEX(createuniqueindextable_123_valid_ucr_iONtable_123_valid(native_id,revision_id)),CONSTRAINTtable_123_valid_cid_revUNIQUE(concept_id,revision_id)USINGINDEX(createuniqueindextable_123_valid_criONtable_123_valid(concept_id,revision_id))")))
  (testing "invalid table name"
    (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
      (are [table-name] (thrown? Exception (ct/collection-constraint-sql non-small-provider table-name))
                        "table_name--;"
                        "table_; DELETE"))))
(deftest collection-constraint-sql-true-test
  (testing "valid table name"
    (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
      (are [table-name query] (= query (tu/remove-spaces-and-new-lines (ct/collection-constraint-sql non-small-provider table-name)))
                              "table_name" "CONSTRAINTtable_name_pkPRIMARYKEY(id),CONSTRAINTtable_name_con_revUNIQUE(provider_id,native_id,revision_id)USINGINDEX(createuniqueindextable_name_ucr_iONtable_name(provider_id,native_id,revision_id)),CONSTRAINTtable_name_cid_revUNIQUE(concept_id,revision_id)USINGINDEX(createuniqueindextable_name_criONtable_name(concept_id,revision_id))"
                              "table_123_valid" "CONSTRAINTtable_123_valid_pkPRIMARYKEY(id),CONSTRAINTtable_123_valid_con_revUNIQUE(provider_id,native_id,revision_id)USINGINDEX(createuniqueindextable_123_valid_ucr_iONtable_123_valid(provider_id,native_id,revision_id)),CONSTRAINTtable_123_valid_cid_revUNIQUE(concept_id,revision_id)USINGINDEX(createuniqueindextable_123_valid_criONtable_123_valid(concept_id,revision_id))")))
  (testing "invalid table name"
    (let [small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
      (are [table-name] (thrown? Exception (ct/collection-constraint-sql small-provider table-name))
                        "table_name--;"
                        "table_; DELETE"))))

(deftest create-collection-indexes-false-test
  (testing "invalid table name"
    (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
      (are [table-name] (thrown? Exception (ct/create-collection-indexes nil non-small-provider table-name))
                        "table_name--;"
                        "table_; DELETE"))))

(deftest create-collection-indexes-true-test
  (testing "invalid table name"
    (let [small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
      (are [table-name] (thrown? Exception (ct/create-collection-indexes nil small-provider table-name))
                        "table_name--;"
                        "table_; DELETE"))))

