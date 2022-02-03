(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-schema-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def base-request {:operation "UPDATE_FIELD"
                   :update-field "OPeNDAPLink"
                   :updates [["ur_1" "https://aws.foo.com"]
                             ["ur_2" "https://aws.bar.com"]]})

(deftest bulk-granule-schema-validation-test
  (are3
   [updates err-msg]
   (let [bulk-update-options {:token (echo-util/login (sys/context) "user1")
                              :accept-format :json
                              :raw? true}
         request (assoc base-request :updates updates)
         {:keys [body status]} (ingest/bulk-update-granules "PROV1"
                                                            request
                                                            bulk-update-options)
         response (json/parse-string body true)]

     (is (= 400 status))
     (is (seq (filter #(= err-msg %) (:errors response)))
         (format "Error message containing [%s] was not found in [%s]"
                 err-msg
                 (pr-str response))))

   "insufficient bulk operation targets"
   []
   "#/updates: expected minimum item count: 1, found: 0"

   "update entries: more than 2"
   [["ur_1" "https://aws.example.fiz" "https://aws.example.baz"]]
   "#/updates/0: expected maximum item count: 2, found: 3"

   "update entries: fewer than 2 (1)"
   [["ur_1"]]
   "#/updates/0: expected minimum item count: 2, found: 1"

   "update entries: fewer than 2 (0)"
   [[]]
   "#/updates/0: expected minimum item count: 2, found: 0"

   ;; Business rules validation

   "blank granule ur"
   [["" "https://example.com/oops-no-ur-empty"]
    ["  " "https://example.com/oops-no-ur-spaces"]
    ["\t" "https://example.com/oops-no-ur-tab"]]
   (str "Empty granule URs are not allowed in bulk update requests. "
        "Found [3] updates with empty granule UR values.")

   "duplicate granule_ur in request"
   [["ur_1" "https://aws.foo.com"]
    ["ur_2" "https://aws.bar.com"]
    ["ur_3" "https://aws.ban.com"]
    ["ur_3" "https://aws.bat.com"]
    ["ur_4" "https://aws.buz.com"]
    ["ur_4" "https://aws.baz.com"]
    ["ur_5" "https://aws.biz.com"]]
   (str "Duplicate granule URs are not allowed in bulk update requests. "
        "Detected the following duplicates [ur_3,ur_4]")))

(deftest operation-validation-test
  (are3 [operation expected-status]
   (let [bulk-update-options {:token (echo-util/login (sys/context) "user1")
                              :accept-format :json
                              :raw? true}
         request (assoc base-request :operation operation)
         {:keys [body status]} (ingest/bulk-update-granules "PROV1"
                                                            request
                                                            bulk-update-options)
         response (json/parse-string body true)]
     (is (= status expected-status))
     (when-not (= 200 expected-status)
       (is (= (format "#/operation: %s is not a valid enum value" operation)
              (first (:errors response))))))
   "valid"
   "UPDATE_FIELD" 200

   "valid"
   "APPEND_TO_FIELD" 200

   "invalid"
   "CROMULENT_OPERATION" 400))

(deftest update-field-validation-test
  (are3 [field expected-status]
   (let [bulk-update-options {:token (echo-util/login (sys/context) "user1")
                              :accept-format :json
                              :raw? true}
         request (assoc base-request :update-field field)
         {:keys [body status]} (ingest/bulk-update-granules "PROV1"
                                                            request
                                                            bulk-update-options)
         response (json/parse-string body true)]
     (is (= status expected-status))
     (when-not (= 200 expected-status)
       (is (= (format "#/update-field: %s is not a valid enum value" field)
              (first (:errors response))))))

   "valid"
   "OPeNDAPLink" 200

   "valid"
   "S3Link" 200

   "invalid"
   "CROMULENT_FIELD" 400))
