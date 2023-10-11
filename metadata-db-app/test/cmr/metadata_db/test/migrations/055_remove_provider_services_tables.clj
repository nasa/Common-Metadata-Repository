(ns cmr.metadata-db.test.migrations.055-remove-provider-services-tables
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.migrations.055-remove-provider-services-tables :as rpst]
   [cmr.common.util :as util :refer [are3]]))

(deftest create-service-table-for-provider-sql-test
  (testing "valid providers given to create service table"
    (are3 [provider-id query]
          (is (= query (#'rpst/create-service-table-for-provider-sql provider-id)))

          "valid provider-id"
          "PROV1"
          "CREATE TABLE PROV1_SERVICES (\n    id NUMBER,\n    concept_id VARCHAR(255) NOT NULL,\n    native_id VARCHAR(1030) NOT NULL,\n    metadata BLOB NOT NULL,\n    format VARCHAR(255) NOT NULL,\n    revision_id INTEGER DEFAULT 1 NOT NULL,\n    revision_date TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,\n    deleted INTEGER DEFAULT 0 NOT NULL,\n    entry_id VARCHAR(255) NOT NULL,\n    entry_title VARCHAR(1030) NOT NULL,\n    delete_time TIMESTAMP WITH TIME ZONE,\n    user_id VARCHAR(30) NULL,\n    provider_id VARCHAR(255) NOT NULL,\n    CONSTRAINT PROV1_services_pk PRIMARY KEY (id),\n    CONSTRAINT PROV1_services_con_rev\n      UNIQUE (provider_id, native_id, revision_id)\n      USING INDEX (create unique index PROV1_services_ucr_i\n                          ON PROV1_services(provider_id, native_id, revision_id)),\n    CONSTRAINT PROV1_services_cid_rev\n      UNIQUE (concept_id, revision_id)\n      USING INDEX (create unique index PROV1_services_cri\n                          ON PROV1_services(concept_id, revision_id)))"))

  (testing "invalid providers given to create service table"
    (are3 [provider-id exception-msg]
          (is (= exception-msg (ex-message (try (#'rpst/create-service-table-for-provider-sql provider-id) (catch Exception e e)))))

          "provider id length > max length (10)"
          "PROV_1123456782345678213456"
          "Provider Id [PROV_1123456782345678213456] exceeds 10 characters"

          "provider id is blank"
          ""
          "Provider Id cannot be blank"

          "provider id in incorrect format"
          "PROV$!*"
          "Provider Id [PROV$!*] is invalid"

          "provider id uses small reserved keywords"
          "SMALL_PROV"
          "Provider Id [SMALL_PROV] is reserved"

          "provider id uses cmr reserved keyword"
          "CMR"
          "Provider Id [CMR] is reserved"

          "provider id is not capitalized"
          "prov1"
          "Provider Id [prov1] is invalid"

          "sql injection is rejected"
          "P1' OR '1' = '1' -- "
          "Provider Id [P1' OR '1' = '1' -- ] exceeds 10 characters")))
