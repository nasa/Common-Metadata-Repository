(ns cmr.metadata-db.test.data.oracle.collection-table
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.data.oracle.collection-table :as ct]
    [cmr.common.util :as util :refer [are3]]))

(deftest collection-constraint-sql-false-test
  (testing "valid table name"
    (are3 [table-name query]
        (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
          (is (= query (ct/collection-constraint-sql non-small-provider table-name))))

        "valid table name"
        "table_name"
        "CONSTRAINT table_name_pk PRIMARY KEY (id), CONSTRAINT table_name_con_rev\n               UNIQUE (native_id, revision_id)\n               USING INDEX (create unique index table_name_ucr_i\n               ON table_name(native_id, revision_id)), CONSTRAINT table_name_cid_rev\n               UNIQUE (concept_id, revision_id)\n               USING INDEX (create unique index table_name_cri\n               ON table_name(concept_id, revision_id))"

        "valid table name with numbers"
        "table_123_valid"
        "CONSTRAINT table_123_valid_pk PRIMARY KEY (id), CONSTRAINT table_123_valid_con_rev\n               UNIQUE (native_id, revision_id)\n               USING INDEX (create unique index table_123_valid_ucr_i\n               ON table_123_valid(native_id, revision_id)), CONSTRAINT table_123_valid_cid_rev\n               UNIQUE (concept_id, revision_id)\n               USING INDEX (create unique index table_123_valid_cri\n               ON table_123_valid(concept_id, revision_id))"))
  (testing "invalid table name"
    (are3 [table-name]
          (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
            (is (thrown? Exception (ct/collection-constraint-sql non-small-provider table-name))))

          "invalid table name"
          "table_name--;"
          nil

          "invalid table name 2"
          "table_; DELETE"
          nil)))

(deftest collection-constraint-sql-true-test
  (testing "valid table name"
    (are3 [table-name query]
          (let [small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
            (is (= query (ct/collection-constraint-sql small-provider table-name))))

          "valid table name"
          "table_name"
          "CONSTRAINT table_name_pk PRIMARY KEY (id), CONSTRAINT table_name_con_rev\n            UNIQUE (provider_id, native_id, revision_id)\n            USING INDEX (create unique index table_name_ucr_i\n            ON table_name(provider_id, native_id, revision_id)), CONSTRAINT table_name_cid_rev\n            UNIQUE (concept_id, revision_id)\n            USING INDEX (create unique index table_name_cri\n            ON table_name(concept_id, revision_id))",

          "valid table name with numbers"
          "table_123_valid"
          "CONSTRAINT table_123_valid_pk PRIMARY KEY (id), CONSTRAINT table_123_valid_con_rev\n            UNIQUE (provider_id, native_id, revision_id)\n            USING INDEX (create unique index table_123_valid_ucr_i\n            ON table_123_valid(provider_id, native_id, revision_id)), CONSTRAINT table_123_valid_cid_rev\n            UNIQUE (concept_id, revision_id)\n            USING INDEX (create unique index table_123_valid_cri\n            ON table_123_valid(concept_id, revision_id))"))
  (testing "invalid table name"
    (are3 [table-name]
          (let [small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
            (is (thrown? Exception (ct/collection-constraint-sql small-provider table-name))))

          "invalid table name"
          "table_name--;"
          nil

          "invalid table name 2"
          "table_; DELETE"
          nil)))

(deftest create-collection-indexes-false-test
  (testing "invalid table name"
    (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
      (are [table-name] (thrown? Exception (ct/create-collection-indexes nil non-small-provider table-name))
                        "table_name--;"
                        "table_; DELETE"))))

(deftest create-collection-indexes-false-test
  (testing "invalid table name"
    (are3 [table-name]
      (let [non-small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small false}]
      (is (thrown? Exception (ct/create-collection-indexes nil non-small-provider table-name))))

          "invalid table name"
          "table_name--;"
          true

          "invalid table name 2"
          "table_; DELETE"
          true)))

(deftest create-collection-indexes-true-test
  (testing "invalid table name"
    (are3 [table-name]
    (let [small-provider {:provider-id "PROV1", :short-name "test provider", :cmr-only false, :small true}]
      (is (thrown? Exception (ct/create-collection-indexes nil small-provider table-name))))

          "invalid table name"
          "table_name--;"
          true

          "invalid table name 2"
          "table_; DELETE"
          true)))
