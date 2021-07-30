(ns cmr.ingest.services.granule-bulk-update-service
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.config :as ingest-config]
   [cmr.ingest.data.granule-bulk-update :as data-granule-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.bulk-update-service :as bulk-update-service]
   [cmr.ingest.services.granule-bulk-update.additional-files.umm-g :as additional-files-umm-g]
   [cmr.ingest.services.granule-bulk-update.checksum.echo10 :as checksum-echo10]
   [cmr.ingest.services.granule-bulk-update.opendap.echo10 :as opendap-echo10]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]
   [cmr.ingest.services.granule-bulk-update.opendap.umm-g :as opendap-umm-g]
   [cmr.ingest.services.granule-bulk-update.s3.echo10 :as s3-echo10]
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]
   [cmr.ingest.services.granule-bulk-update.s3.umm-g :as s3-umm-g]
   [cmr.ingest.services.ingest-service :as ingest-service]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.umm-spec.migration.version.core :as vc]
   [cmr.umm-spec.umm-json :as umm-json]
   [cmr.umm-spec.umm-spec-core :as umm-spec]
   [cmr.umm-spec.versioning :as versioning]))

(def granule-bulk-update-schema
  (js/json-string->json-schema (slurp (io/resource "granule_bulk_update_schema.json"))))

(defconfig granule-bulk-update-chunk-size
  "Default size to partition granule-bulk-update instructions into."
  {:default 100
   :type Long})

(defn- validate-granule-bulk-update-json
  "Validate the request against the schema and return the parsed request."
  [json]
  (js/validate-json! granule-bulk-update-schema json))

(defn- duplicates
  "Return a seq of any non-unique entries in a collection or nil."
  [coll]
  (seq (for [[id freq] (frequencies coll)
             :when (> freq 1)]
         id)))

(defmulti update->instruction
  "Returns the granule bulk update instruction for a single update item"
  (fn [event-type update-field item]
    (keyword update-field)))

(defmethod update->instruction :AdditionalFile
  [event-type update-field item]
  (let [{:keys [GranuleUR Files]} item]
    {:event-type event-type
     :granule-ur GranuleUR
     :new-value Files}))

(defmethod update->instruction :default
  [event-type update-field item]
  (let [[granule-ur value] item]
    {:event-type event-type
     :granule-ur granule-ur
     :new-value value}))

(defn- request->instructions
  "Returns granule bulk update instructions for the given request"
  [parsed-json]
  (let [{:keys [operation update-field updates]} parsed-json
        event-type (string/lower-case (str operation ":" update-field))]
    (map (partial update->instruction event-type update-field) updates)))

(defn- validate-granule-bulk-update-no-duplicate-urs
  "Validate no duplicate URs exist within the list of instructions"
  [urs]
  (when-let [duplicate-urs (duplicates urs)]
    (errors/throw-service-errors
     :bad-request
     [(format (str "Duplicate granule URs are not allowed in bulk update requests. "
                   "Detected the following duplicates [%s]")
              (string/join "," duplicate-urs))]))
  urs)

(defn- validate-granule-bulk-update-no-blank-urs
  "Validate no blank URs exist in the list."
  [urs]
  (when-let [blank-urs (seq (filter string/blank? urs))]
    (errors/throw-service-errors
     :bad-request
     [(format (str "Empty granule URs are not allowed in bulk update requests. "
                   "Found [%d] updates with empty granule UR values.")
              (count blank-urs))]))
  urs)

(defn- validate-and-parse-bulk-granule-update
  "Perform validation operations on bulk granule update requests."
  [context json-body provider-id]
  ;; validate request against schema
  (validate-granule-bulk-update-json json-body)
  (let [request (json/parse-string json-body true)]
    ;; validate granule UR
    (->> request
         request->instructions
         (map :granule-ur)
         validate-granule-bulk-update-no-blank-urs
         validate-granule-bulk-update-no-duplicate-urs)
    request))

(defn- publish-instructions-partitioned
  "Publish bulk granule update events in partitioned amounts."
  [context provider-id user-id task-id instructions partition-size]
  (let [chunked-instructions (partition-all partition-size instructions)]
    (info (format
           (str "Bulk granule update request [%s] by user [%s] "
                "preparing [%d] updates in [%d] jobs "
                "with job size of [%d].")
           task-id
           user-id
           (count instructions)
           (count chunked-instructions)
           partition-size))

    (doseq [ins-list chunked-instructions]
      (ingest-events/publish-gran-bulk-update-event
       context
       (ingest-events/granules-bulk-event provider-id
                                          task-id
                                          user-id
                                          ins-list)))))

(defn validate-and-save-bulk-granule-update
  "Validate the granule bulk update request, save rows to the db for task
  and granule statuses, and queue bulk granule update. Return task id, which comes
  from the db save."
  [context provider-id json-body user-id]
  (let [request (validate-and-parse-bulk-granule-update context json-body provider-id)
        instructions (request->instructions request)

        task-id (try
                  (data-granule-bulk-update/create-granule-bulk-update-task
                   context
                   provider-id
                   user-id
                   json-body
                   instructions)
                  (catch Exception e
                    (error "An exception occurred saving a bulk granule update request:" e)
                    (let [message (.getMessage e)]
                      (errors/throw-service-errors
                       :internal-error
                       [(str "There was a problem saving a bulk granule update request."
                             "Please try again, if the problem persists please contact "
                             (ingest-config/cmr-support-email)
                             ".")]))))]

    ;; Queue the granules bulk update events
    (publish-instructions-partitioned
     context
     provider-id
     user-id
     task-id
     instructions
     (granule-bulk-update-chunk-size))
    task-id))

(defn handle-granules-bulk-event
  "For each granule-ur, queue a granule bulk update message"
  [context provider-id task-id bulk-update-params user-id]
  (doseq [instruction bulk-update-params]
    (ingest-events/publish-gran-bulk-update-event
     context
     (ingest-events/ingest-granule-bulk-update-event provider-id task-id user-id instruction))))

(defn- update-umm-g-urls
  "Takes the UMM-G granule concept and update s3 urls in the format of
  {:cloud <cloud_url> :on-prem <on_prem_url>}. Update the UMM-G granule metadata with the
  s3 urls in the latest UMM-G version.
  Returns the granule concept with the updated metadata."
  [concept urls update-umm-g-urls-fn]
  (let [{:keys [format metadata]} concept
        source-version (umm-spec/umm-json-version :granule format)
        parsed-metadata (json/decode metadata true)
        target-version (versioning/current-version :granule)
        migrated-metadata (util/remove-nils-empty-maps-seqs
                           (vc/migrate-umm
                            nil :granule source-version target-version parsed-metadata))
        updated-metadata (umm-json/umm->json (update-umm-g-urls-fn migrated-metadata urls))
        updated-format (mt/format->mime-type {:format :umm-json
                                              :version target-version})]
    (assoc concept :metadata updated-metadata :format updated-format)))

(defmulti update-opendap-url
  "Add OPeNDAP url to the given granule concept."
  (fn [context concept grouped-urls]
    (mt/format-key (:format concept))))

(defmethod update-opendap-url :echo10
  [context concept grouped-urls]
  (opendap-echo10/update-opendap-url concept grouped-urls))

(defmethod update-opendap-url :umm-json
  [context concept grouped-urls]
  (update-umm-g-urls concept grouped-urls opendap-umm-g/update-opendap-url))

(defmethod update-opendap-url :default
  [context concept grouped-urls]
  (errors/throw-service-errors
   :invalid-data [(format "Adding OPeNDAP url is not supported for format [%s]" (:format concept))]))

(defmulti append-opendap-url
  "Append OPeNDAP url to the given granule concept."
  (fn [context concept grouped-urls]
    (mt/format-key (:format concept))))

(defmethod append-opendap-url :echo10
  [context concept grouped-urls]
  (opendap-echo10/append-opendap-url concept grouped-urls))

(defmethod append-opendap-url :umm-json
  [context concept grouped-urls]
  (update-umm-g-urls concept grouped-urls opendap-umm-g/append-opendap-url))

(defmethod append-opendap-url :default
  [context concept grouped-urls]
  (errors/throw-service-errors
   :invalid-data [(format "Append OPeNDAP url is not supported for format [%s]" (:format concept))]))

(defmulti add-s3-url
  "Add s3 url to the given granule concept."
  (fn [context concept urls]
    (mt/format-key (:format concept))))

(defmethod add-s3-url :echo10
  [context concept urls]
  (s3-echo10/update-s3-url concept urls))

(defmethod add-s3-url :umm-json
  [context concept urls]
  (update-umm-g-urls concept urls s3-umm-g/update-s3-url))

(defmethod add-s3-url :default
  [context concept urls]
  (errors/throw-service-errors
   :invalid-data [(format "Adding s3 urls is not supported for format [%s]" (:format concept))]))

(defmulti append-s3-url
  "Add s3 url to the given granule concept."
  (fn [context concept urls]
    (mt/format-key (:format concept))))

(defmethod append-s3-url :echo10
  [context concept urls]
  (s3-echo10/append-s3-url concept urls))

(defmethod append-s3-url :umm-json
  [context concept urls]
  (update-umm-g-urls concept urls s3-umm-g/append-s3-url))

(defmethod append-s3-url :default
  [context concept urls]
  (errors/throw-service-errors
   :invalid-data [(format "Appending s3 urls is not supported for format [%s]" (:format concept))]))

(defmulti update-checksum
  "Add checksum to the given granule concept."
  (fn [context concept checksum]
    (mt/format-key (:format concept))))

(defmethod update-checksum :echo10
  [context concept checksum]
  (checksum-echo10/update-checksum concept checksum))

(defmethod update-checksum :umm-json
  [context concept checksum]
  (errors/throw-service-errors
   :invalid-data ["Updating checksum for UMM_JSON is coming soon!"]))

(defmethod update-checksum :default
  [context concept checksum]
  (errors/throw-service-errors
   :invalid-data [(format "Updating checksum is not supported for format [%s]" (:format concept))]))

(defmulti update-additional-files
  "Update AdditionalFiles in given granule concept."
  (fn [context concept checksum]
    (mt/format-key (:format concept))))

(defmethod update-additional-files :echo10
  [context concept checksum]
  (errors/throw-service-errors
   :invalid-data ["Updating AdditionalFiles for ECHO10 is coming soon!"]))

(defmethod update-additional-files :umm-json
  [context concept checksum]
  (additional-files-umm-g/update-additional-files concept checksum))

(defmethod update-additional-files :default
  [context concept checksum]
  (errors/throw-service-errors
   :invalid-data [(format "Updating AdditionalFiles is not supported for format [%s]" (:format concept))]))

(defmulti update-granule-concept
  "Perform the update of the granule concept."
  (fn [context concept bulk-update-params user-id]
    (keyword (:event-type bulk-update-params))))

(defn- modify-opendap-link*
  "For OPeNDAP links, there may be at most 2 urls, 1 cloud, 1 on-prem.
  Updates containing a url type that is already present on the concept
  will fail with an exception.
  Successful updates will return the updated concept."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [format metadata]} concept
        {:keys [granule-ur new-value]} bulk-update-params
        grouped-urls (opendap-util/validate-url new-value)
        updated-concept (xf context concept grouped-urls)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defmethod update-granule-concept :update_field:opendaplink
  [context concept bulk-update-params user-id]
  (modify-opendap-link*
   context concept bulk-update-params user-id update-opendap-url))

(defmethod update-granule-concept :append_to_field:opendaplink
  [context concept bulk-update-params user-id]
  (modify-opendap-link*
   context concept bulk-update-params user-id append-opendap-url))

(defn- modify-s3-link*
  "Modify the S3Link data for the given concept with the provided URLs
  using the provided transform function.
  S3 links will be added to the existing concept. If a duplicate S3 link
  is provided it will be ignored."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [format metadata]} concept
        {:keys [granule-ur new-value]} bulk-update-params
        urls (s3-util/validate-url new-value)
        ;; invoke the appropriate transform
        updated-concept (xf context concept urls)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defmethod update-granule-concept :update_field:s3link
  [context concept bulk-update-params user-id]
  (modify-s3-link*
   context concept bulk-update-params user-id add-s3-url))

(defmethod update-granule-concept :append_to_field:s3link
  [context concept bulk-update-params user-id]
  (modify-s3-link*
   context concept bulk-update-params user-id append-s3-url))

(defn- modify-checksum*
  "Add or update the checksum value and algorithm for the given concept with the provided values
  using the provided transform function."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [format metadata]} concept
        {:keys [granule-ur new-value]} bulk-update-params
        ;; invoke the appropriate transform - for checksum, there is only one option (update)
        updated-concept (xf context concept new-value)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defmethod update-granule-concept :update_field:checksum
  [context concept bulk-update-params user-id]
  (modify-checksum*
   context concept bulk-update-params user-id update-checksum))

(defn- modify-additional-files*
  "Add or update the checksum value and algorithm for the given concept with the provided values
  using the provided transform function."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [format metadata]} concept
        {:keys [granule-ur new-value]} bulk-update-params
        ;; invoke the appropriate transform - for checksum, there is only one option (update)
        updated-concept (xf context concept new-value)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defmethod update-granule-concept :update_field:additionalfile
  [context concept bulk-update-params user-id]
  (modify-additional-files*
   context concept bulk-update-params user-id update-additional-files))

(defmethod update-granule-concept :default
  [context concept bulk-update-params user-id]
  (warn "No default implementation for update-granule-concept"))

(defn- update-granule-concept-and-status
  "Perform update for the granule concept and granule bulk update status."
  [context task-id concept granule-ur bulk-update-params user-id]
  (if-let [updated-concept (update-granule-concept context concept bulk-update-params user-id)]
    (do
      (ingest-service/save-granule context updated-concept)
      (data-granule-bulk-update/update-bulk-update-task-granule-status
       context task-id granule-ur bulk-update-service/updated-status ""))
    (data-granule-bulk-update/update-bulk-update-task-granule-status
     context task-id granule-ur bulk-update-service/skipped-status
     (format (str "Granule with granule-ur [%s] in task-id [%s] is not updated "
                  "because the metadata format [%s] is not supported.")
             granule-ur task-id (:format concept)))))

(defn handle-granule-bulk-update-event
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [granule-ur]} bulk-update-params]
    (try
      (if-let [concept (mdb/find-latest-concept
                        context {:provider-id provider-id :granule-ur granule-ur} :granule)]
        (if (:deleted concept)
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status
           (format (str "Granule with granule-ur [%s] on provider [%s] in task-id [%s] "
                        "is deleted. Can not be updated.")
                   granule-ur provider-id task-id))
          ;; granule found and not deleted, update the granule
          (update-granule-concept-and-status
           context task-id concept granule-ur bulk-update-params user-id))
        ;; granule not found
        (data-granule-bulk-update/update-bulk-update-task-granule-status
         context task-id granule-ur bulk-update-service/failed-status
         (format "Granule UR [%s] in task-id [%s] does not exist." granule-ur task-id)))
      (catch clojure.lang.ExceptionInfo ex-info
        (error "handle-granule-bulk-update-event caught ExceptionInfo:" ex-info)
        (if (= :conflict (:type (.getData ex-info)))
          ;; Concurrent update - re-queue concept update
          (ingest-events/publish-ingest-event
           context
           (ingest-events/ingest-granule-bulk-update-event
            provider-id task-id user-id bulk-update-params))
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status (.getMessage ex-info))))
      (catch Exception e
        (error "handle-granule-bulk-update-event caught exception:" e)
        (let [message (or (.getMessage e) bulk-update-service/default-exception-message)]
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status message))))))

(defn cleanup-bulk-granule-task-table
  "Delete and export all granule bulk update tasks marked ready for deletion"
  [context]
  (try
    (data-granule-bulk-update/cleanup-bulk-granule-tasks context)
    (catch Exception e
      (errors/throw-service-error
       :error
       [(format "Exception caught while attempting to cleanup granule bulk update tasks: %s" e)]))))

(defn update-completed-task-status!
  "Finds incomplete bulk granule update tasks and marks them as complete if
  no granules remain to be processed."
  [context]
  (when-let [incomplete-tasks (data-granule-bulk-update/get-incomplete-granule-task-ids context)]
    (doseq [task incomplete-tasks]
      (when (data-granule-bulk-update/task-completed? context task)
        (data-granule-bulk-update/mark-task-complete context task)))))
