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
      (is (some? (re-find #"unique constraint \(METADATA_DB\.TAG_ASSOCS_CID_REV\) violated"
                          (:error-message (oc/save-concept db
                                                           {:provider-id "PROV1"
                                                            :small false}
                                                           tag-assoc))))))))
