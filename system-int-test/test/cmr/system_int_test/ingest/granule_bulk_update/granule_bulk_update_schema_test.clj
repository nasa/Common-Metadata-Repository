(ns cmr.system-int-test.ingest.granule-bulk-update.granule-bulk-update-schema-test
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.message-queue.test.queue-broker-side-api :as qb-side-api]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def base-request {:operation "UPDATE_FIELD"
                   :update-field "OPeNDAPLink"
                   :updates
                   [["ur_1" "https://aws.foo.com"]
                    ["ur_2" "https://aws.bar.com"]]})

(deftest bulk-granule-schema-validation-test
  (are3
   [mutate err-msg]
   (let [bulk-update-options {:token (echo-util/login (sys/context) "user1")
                              :accept-format :json
                              :raw? true}
         request (mutate base-request)
         {:keys [body status]} (ingest/bulk-update-granules "PROV1"
                                                            request
                                                            bulk-update-options)
         response (json/parse-string body true)]

     (is (= 400 status))
     (is (seq (filter #(= err-msg %) (:errors response)))
         (format "Error message containing [%s] was not found in [%s]"
                 err-msg
                 (pr-str response))))

   ;; Pass a mutate function to modify the base-request for the input

   ;; Schema validations
   "insufficient bulk operation targets"
   (fn [req] (assoc req :updates []))
   "#/updates: expected minimum item count: 1, found: 0"

   "update entries: more than 2"
   (fn [req] (update req :updates conj ["ur_3" "https://aws.example.fiz" "https://aws.example.baz"]))
   "#/updates/2: expected maximum item count: 2, found: 3"

   "update entries: fewer than 2 (1)"
   (fn [req] (update req :updates conj ["ur_3"]))
   "#/updates/2: expected minimum item count: 2, found: 1"

   "update entries: fewer than 2 (0)"
   (fn [req] (update req :updates conj []))
   "#/updates/2: expected minimum item count: 2, found: 0"

   "invalid operation"
   (fn [req] (assoc req :operation "CROMULANT_OPERATION"))
   "#/operation: CROMULANT_OPERATION is not a valid enum value"

   "invalid update-field"
   (fn [req] (assoc req :update-field "CROMULANT_FIELD"))
   "#/update-field: CROMULANT_FIELD is not a valid enum value"

   ;; Business rules validation

   "duplicate granule_ur in request"
   (fn [req] (update req
                    :updates
                    conj
                    ["ur_4" "https://aws.fiz.com"]
                    ["ur_4" "https://aws.ban.com"]
                    ["ur_4" "https://aws.bat.com"]
                    ["ur_5" "https://aws.buz.com"]
                    ["ur_5" "https://aws.baz.com"]))
   (str "Duplicate granule URs are not allowed in bulk update requests. "
        "Detected the following duplicates [ur_4,ur_5]")))
