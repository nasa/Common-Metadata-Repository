(ns cmr.metadata-db.int-test.concepts.unique-constraints-tag-test
  (:require
   [clojure.test :refer :all]
   [cmr.metadata-db.config :as mdb-config]
   [cmr.metadata-db.data.oracle.concepts :as oc]
   [cmr.metadata-db.int-test.concepts.utils.interface :as concepts]
   [cmr.metadata-db.int-test.utility :as util]))

(use-fixtures :each (util/reset-database-fixture {:provider-id "PROV1" :small false}))

(deftest ^:oracle unique-constraint-catch-test
  (testing "saving tags with duplicate revision-id and concept-id should return an error"
    (let [db (mdb-config/db-spec "metadata-db")
          coll (concepts/create-and-save-concept :collection "PROV1" 1)
          tag (concepts/create-and-save-concept :tag "CMR" 1)
          tag-assoc (concepts/create-concept :tag-association
                                             coll
                                             tag
                                             1
                                             {:concept-id "TA120000-PROV1"
                                              :revision-id 1})]
      ;; first save should be fine
      (is (= 201 (:status (util/save-concept tag-assoc))))

      ;; check URL for conflict message on duplicate save
      (is (= 409 (:status (util/save-concept tag-assoc))))

      ;; Save directly to DB to avoid validations
      (is (seq (re-find #"unique constraint \(METADATA_DB\.TAG_ASSOCS_CID_REV\) violated"
                        (:error-message (oc/save-concept db
                                                         {:provider-id "PROV1"
                                                          :small false}
                                                         tag-assoc)))))))

  (testing "saving tags with duplicate revision-id and native-id should return an error"
    (let [db (mdb-config/db-spec "metadata-db")
          coll (concepts/create-and-save-concept :collection "PROV1" 1)
          tag (concepts/create-and-save-concept :tag "CRI" 1)
          tag1 (concepts/create-concept :tag-association
                                        coll
                                        tag
                                        1
                                        {:concept-id "TA120000-PROV1"
                                         :native-id "native-id"
                                         :revision-id 1})

          tag2 (concepts/create-concept :tag-association
                                        coll
                                        tag
                                        1
                                        {:concept-id "TA140000-PROV1"
                                         :native-id "native-id"
                                         :revision-id 1})]

      ;; Save directly to DB to avoid validations
      (is (nil? (oc/save-concept db
                                 {:provider-id "PROV1"
                                  :small false}
                                 tag2)))

      ;; negative case cannot be tested because of running on a single machine
      (is (= 409 (:status (util/save-concept tag1)))))))

(deftest handle-oracle-exception-test
  (are [err-string err-code]
      (= err-code
         (-> err-string
             (ex-info {:note "java.sql.BatchException normally"})
             oc/handle-oracle-exception
             :error))

    "ORA-00001: unique constraint (METADATA_DB.TAG_ASSOCS_CRI) violated"
    :revision-id-conflict

    "ORA-00001: unique constraint (METADATA_DB.ASF_GRANULES_CRI) violated"
    :revision-id-conflict

    "ORA-00001: unique constraint (METADATA_DB.TAG_ASSOCS_CON_REV) violated"
    :revision-id-conflict

    "ORA-01722: invalid number"
    :unknown-error))
