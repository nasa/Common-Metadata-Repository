(ns cmr.system-int-test.ingest.bulk-update.bulk-granule-update-schema-test
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

(def base-request {:name "sample bulk update"
                   :operation "UPDATE_FIELD"
                   :update-field "OPeNDAPLink"
                   :updates
                   [["ur_1" "s3://aws.foo.com"]
                    ["ur_2" "s3://aws.bar.com"]]})

(deftest bulk-granule-schema-validation-test
  (are3
   [update-fn err-msg]
   (let [bulk-update-options {:token (echo-util/login (sys/context) "user1")
                              :accept-format :json
                              :raw? true}
         request (update-fn base-request)
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
   (fn [m] (assoc m :updates []))
   "#/updates: expected minimum item count: 1, found: 0"

   "update entries: more than 2"
   (fn [m] (update m :updates conj ["ur_3" "s3://aws.example.fiz" "s3://aws.example.baz"]))
   "#/updates/2: expected maximum item count: 2, found: 3"

   "update entries: fewer than 2 (1)"
   (fn [m] (update m :updates conj ["ur_3"]))
   "#/updates/2: expected minimum item count: 2, found: 1"

   "update entries: fewer than 2 (0)"
   (fn [m] (update m :updates conj []))
   "#/updates/2: expected minimum item count: 2, found: 0"

   "duplicate granule_ur in request"
   (fn [m] (update m
                  :updates
                  conj
                  ["ur_1" "s3://aws.buz.com"]
                  ["ur_2" "s3://aws.fiz.com"]
                  ["ur_3" "s3://aws.ban.com"]))
   (str "error creating granule bulk update task: "
        "Duplicate granule URs are not allowed in bulk update requests. "
        "Detected the following duplicates [ur_1,ur_2]")

   "invalid operation"
   (fn [m] (assoc m :operation "CROMULANT_OPERATION"))
   "#/operation: CROMULANT_OPERATION is not a valid enum value"

   "invalid update-field"
   (fn [m] (assoc m :update-field "CROMULANT_FIELD"))
   "#/update-field: CROMULANT_FIELD is not a valid enum value"))
