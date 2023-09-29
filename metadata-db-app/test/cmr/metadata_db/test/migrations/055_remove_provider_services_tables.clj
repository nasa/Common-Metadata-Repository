(ns cmr.metadata-db.test.migrations.055-remove-provider-services-tables
  (:require
    [clojure.test :refer :all]
    [cmr.metadata-db.migrations.055-remove-provider-services-tables :as rpst]
    [cmr.common.util :as util :refer [are3]]))

(deftest create-service-table-for-provider-sql-test
  (testing "valid providers"
    (are3 [provider-id query]
          (is (= query (#'rpst/create-service-table-for-provider-sql provider-id)))

          "valid provider-id"
          "PROV1"
          "CREATE TABLE PROV1_SERVICES (\n    id NUMBER,\n    concept_id VARCHAR(255) NOT NULL,\n    native_id VARCHAR(1030) NOT NULL,\n    metadata BLOB NOT NULL,\n    format VARCHAR(255) NOT NULL,\n    revision_id INTEGER DEFAULT 1 NOT NULL,\n    revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,\n    deleted INTEGER DEFAULT 0 NOT NULL,\n    entry_id VARCHAR(255) NOT NULL,\n    entry_title VARCHAR(1030) NOT NULL,\n    delete_time TIMESTAMP WITH TIME ZONE,\n    user_id VARCHAR(30) NULL,\n    provider_id VARCHAR(255) NOT NULL,\n    CONSTRAINT PROV1_services_pk PRIMARY KEY (id),\n    CONSTRAINT PROV1_services_con_rev\n      UNIQUE (provider_id, native_id, revision_id)\n      USING INDEX (create unique index PROV1_services_ucr_i\n                          ON PROV1_services(provider_id, native_id, revision_id)),\n    CONSTRAINT PROV1_services_cid_rev\n      UNIQUE (concept_id, revision_id)\n      USING INDEX (create unique index PROV1_services_cri\n                          ON PROV1_services(concept_id, revision_id)))"))

  (testing "invalid providers"
    (are3 [provider-id]
          (is (thrown? Exception (#'rpst/create-service-table-for-provider-sql provider-id)))

          "provider id length > max length (10)"
          "prov_id_1123456782345678213456"

          "provider id is blank"
          ""

          "provider id in incorrect format"
          "provider has spaces and wierd symbols $%^&"

          "provider id uses reserved keywords"
          "small_prov_id1"

          "provider id uses reserved keyword 2"
          "CMR_prov_id"

          "provider id is not capitalized"
          "prov1"

          "sql injection is rejected"
          "small_provider' OR '1' = '1' -- ")))
