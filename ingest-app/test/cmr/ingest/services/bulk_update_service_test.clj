(ns cmr.ingest.services.bulk-update-service-test
  (:require
   [cheshire.core :as json]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.common.validations.json-schema :as schema-validation]
   [cmr.ingest.services.granule-bulk-update-service :as granule-bulk-update]))

(def sample-message
  {:name "add opendap links"
   :operation :UPDATE_FIELD
   :update-field "OPeNDAPLink"
   :updates [["SC:AE_5DSno.002:30500511" "url1234"]
             ["SC:AE_5DSno.002:30500512" "url3456"]
             ["SC:AE_5DSno.002:30500513" "url5678"]]})

(def sample-message-wrong-op
  {:name "add opendap links"
   :operation :UPDATE_FIELD
   :update-field "AdditionalFile"
   :updates [["SC:AE_5DSno.002:30500511" "url1234"]
             ["SC:AE_5DSno.002:30500512" "url3456"]
             ["SC:AE_5DSno.002:30500513" "url5678"]]})

(deftest validate-json-test
  (testing "granule bulk update schema"
    (testing "required fields"
      (are3 [field]
            (let [invalid-json (json/generate-string (dissoc sample-message field))]
              (is (thrown-with-msg?
                   clojure.lang.ExceptionInfo
                   #"required key"
                   (schema-validation/validate-json!
                    granule-bulk-update/granule-bulk-update-schema
                    invalid-json))))
            "Missing :operation" :operation
            "Missing :update-field" :update-field
            "Missing :updates" :updates)))

  (testing "granule bulk update schema wrong op (needs update before merge)"
    (let [invalid-json (json/generate-string sample-message-wrong-op)]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"#/updates/0: expected type: JSONObject, found: JSONArray"
           (schema-validation/validate-json!
            granule-bulk-update/granule-bulk-update-schema
            invalid-json))))))
