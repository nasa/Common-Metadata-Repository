(ns cmr.ingest.services.granule-bulk-update-service
  (:require
   [cheshire.core :as json]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common-app.config :as common-app-config]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.time-keeper :as time-keeper]
   [cmr.common.util :as util]
   [cmr.common.validations.json-schema :as js]
   [cmr.ingest.data.granule-bulk-update :as data-granule-bulk-update]
   [cmr.ingest.data.ingest-events :as ingest-events]
   [cmr.ingest.services.bulk-update-service :as bulk-update-service]
   [cmr.ingest.services.granule-bulk-update.additional-file.umm-g :as additional-file-umm-g]
   [cmr.ingest.services.granule-bulk-update.checksum.echo10 :as checksum-echo10]
   [cmr.ingest.services.granule-bulk-update.format.echo10 :as format-echo10]
   [cmr.ingest.services.granule-bulk-update.mime-type.echo10 :as mime-type-echo10]
   [cmr.ingest.services.granule-bulk-update.mime-type.umm-g :as mime-type-umm-g]
   [cmr.ingest.services.granule-bulk-update.online-resource-url.echo10 :as online-resource-url-echo10]
   [cmr.ingest.services.granule-bulk-update.opendap.echo10 :as opendap-echo10]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]
   [cmr.ingest.services.granule-bulk-update.opendap.umm-g :as opendap-umm-g]
   [cmr.ingest.services.granule-bulk-update.size.echo10 :as size-echo10]
   [cmr.ingest.services.granule-bulk-update.s3.echo10 :as s3-echo10]
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]
   [cmr.ingest.services.granule-bulk-update.s3.umm-g :as s3-umm-g]
   [cmr.ingest.services.granule-bulk-update.utils.umm-g :as umm-g-util]
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
         (util/html-escape id))))

(defn- invalid-update-error
  "Throws error in the case the wrong format for the updates field was used for a
   given operation:update-field combination"
  [event-type]
  (errors/throw-service-errors
   :bad-request
   [(format (str "Bulk Granule Update failed - invalid update format specified for the "
                 "operation:update-field combination [%s].")
            (util/html-escape event-type))]))

(defn- invalid-event-type
  "Throws error in the case the operation:update-field combination is invalid."
  [event-type]
  (errors/throw-service-errors
   :bad-request
   [(format (str "Bulk Granule Update failed - the operation:update-field combination [%s] is invalid.")
            (util/html-escape event-type))]))

(defn- update->instruction-update-field-additionalfile
  "Returns the granule bulk update instruction for a single additionalfile upate."
  [event-type item]
  (if-not (map? item)
    (invalid-update-error event-type)
    (let [{:keys [GranuleUR Files]} item]
      {:event-type event-type
       :granule-ur GranuleUR
       :new-value Files})))

(defn- update->instruction-update-field-links
  "Returns the granule bulk update instruction for a single links update."
  [event-type item]
  (if-not (map? item)
    (invalid-update-error event-type)
    (let [{:keys [GranuleUR Links]} item]
      {:event-type event-type
       :granule-ur GranuleUR
       :new-value Links})))

(defn- update->instruction-update-type-opendaplink
  "Returns the granule bulk update instruction for a single opendaplink update."
  [event-type item]
  (cond
    (vector? item)
    (let [[granule-ur value] item]
      {:event-type event-type
       :granule-ur granule-ur
       :new-value value})

    (string? item)
    {:event-type event-type
     :granule-ur item}

    :else (invalid-update-error event-type)))

(defn- update->instruction
  "Returns the granule bulk update instruction for a single update item"
  [event-type item]
  (case (keyword event-type)
    :update_field:additionalfile (update->instruction-update-field-additionalfile event-type item)
    (or
     :update_field:mimetype
     :update_field:onlineresourceurl
     :update_field:onlineaccessurl
     :update_field:browseimageurl
     :append_to_field:onlineresourceurl
     :append_to_field:onlineaccessurl
     :append_to_field:browseimageurl
     :remove_field:onlineresourceurl
     :remove_field:onlineaccessurl
     :remove_field:browseimageurl
     :update_field:relatedurl
     :append_to_field:relatedurl
     :remove_field:relatedurl) (update->instruction-update-field-links event-type item)
    :update_type:opendaplink (update->instruction-update-type-opendaplink event-type item)
    (if-not (vector? item)
      (invalid-update-error event-type)
      (let [[granule-ur value] item]
        {:event-type event-type
         :granule-ur granule-ur
         :new-value value}))))

(defn- request->instructions
  "Returns granule bulk update instructions for the given request"
  [parsed-json]
  (let [{:keys [operation update-field updates]} parsed-json
        event-type (string/lower-case (str operation ":" update-field))]
    (map (partial update->instruction event-type) updates)))

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
  [_context json-body _provider-id]
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

;; TODO Step 5
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

;; TODO Step 3
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
                      (log/error message)
                      (errors/throw-service-errors
                       :internal-error
                       [(str "There was a problem saving a bulk granule update request."
                             "Please try again, if the problem persists please contact "
                             (common-app-config/cmr-support-email)
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

(defn- update-umm-g-metadata
  "Takes the UMM-G granule concept and update with the given update fn and update-values.
  Returns the granule concept with the updated metadata."
  [context concept update-values user-id update-umm-g-metadata-fn]
  (let [{:keys [format metadata]} concept
        source-version (umm-spec/umm-json-version :granule format)
        parsed-metadata (json/decode metadata true)
        target-version (versioning/current-version :granule)
        migrated-metadata (util/remove-nils-empty-maps-seqs
                           (vc/migrate-umm
                            nil :granule source-version target-version parsed-metadata))
        updated-metadata (umm-json/umm->json (update-umm-g-metadata-fn context migrated-metadata update-values))
        updated-format (mt/format->mime-type {:format :umm-json
                                              :version target-version})]
    (-> concept
        (assoc :metadata updated-metadata
               :format updated-format
               :revision-date (time-keeper/now)
               :user-id user-id)
        (update :revision-id inc))))

(defn- update-opendap-url
  "Add OPeNDAP url to the given granule concept."
  [context concept grouped-urls user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (opendap-echo10/update-opendap-url concept grouped-urls)
    :umm-json (update-umm-g-metadata context concept grouped-urls user-id opendap-umm-g/update-opendap-url)
    (errors/throw-service-errors
     :invalid-data [(format "Adding OPeNDAP url is not supported for format [%s]" (:format concept))])))

(defn- update-opendap-type
  "Updates the opendap type in the provided concepts.
   Note: grouped-urls will be nil for this update, as there is no input needed."
  [context concept grouped-urls user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (opendap-echo10/update-opendap-type concept grouped-urls)
    :umm-json (update-umm-g-metadata context concept grouped-urls user-id opendap-umm-g/update-opendap-type)
    (errors/throw-service-errors
     :invalid-data [(format "Updating opendap type is not supported for format [%s]" (:format concept))])))

(defn- append-opendap-url
  "Append OPeNDAP url to the given granule concept."
  [context concept grouped-urls user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (opendap-echo10/append-opendap-url concept grouped-urls)
    :umm-json (update-umm-g-metadata context concept grouped-urls user-id opendap-umm-g/append-opendap-url)
    (errors/throw-service-errors
     :invalid-data [(format "Append OPeNDAP url is not supported for format [%s]" (:format concept))])))

(defn- add-s3-url
  "Add s3 url to the given granule concept."
  [context concept urls user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (s3-echo10/update-s3-url concept urls)
    :umm-json (update-umm-g-metadata context concept urls user-id s3-umm-g/update-s3-url)
    (errors/throw-service-errors
     :invalid-data [(format "Adding s3 urls is not supported for format [%s]" (:format concept))])))

(defn- append-s3-url
  "Add s3 url to the given granule concept."
  [context concept urls user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (s3-echo10/append-s3-url concept urls)
    :umm-json (update-umm-g-metadata context concept urls user-id s3-umm-g/append-s3-url)
    (errors/throw-service-errors
     :invalid-data [(format "Appending s3 urls is not supported for format [%s]" (:format concept))])))

(defn- update-checksum
  "Add checksum to the given granule concept."
  [_context concept checksum _user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (checksum-echo10/update-checksum concept checksum)
    :umm-json (errors/throw-service-errors
               :invalid-data ["Updating checksum is not supported for UMM-G. Please use update-field: AdditionalFile"])
    (errors/throw-service-errors
     :invalid-data [(format "Updating checksum is not supported for format [%s]" (:format concept))])))

(defn- update-size
  "Add/update size to the given granule concept."
  [_context concept size _user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (size-echo10/update-size concept size)
    (errors/throw-service-errors
     :invalid-data [(format "Updating size is not supported for format [%s]" (:format concept))])))

(defn- update-format
  "Add/update format to the given granule concept."
  [_context concept fmt _user-id]
  (condp = (mt/format-key (:format concept))
    :echo10 (format-echo10/update-format concept fmt)
    (errors/throw-service-errors
     :invalid-data [(format "Updating size is not supported for format [%s]" (:format concept))])))

(defn- validate-links-for-update-mime-type
  "Validate that the passed in links contain a URL and a MimeType"
  [links]
  (doseq [link links]
    (when (or (nil? (:URL link))
              (nil? (:MimeType link)))
      (errors/throw-service-errors
       :invalid-data
       ["Each provided link must have a URL and a MimeType property. Please make sure to supply both."]))))

(defn- update-mime-type
  "Add/update mime types for RelatedUrl links in a given granule."
  [context concept links user-id]
  (validate-links-for-update-mime-type links)
  (condp = (mt/format-key (:format concept))
    :echo10 (mime-type-echo10/update-mime-type concept links)
    :umm-json (update-umm-g-metadata context concept links user-id mime-type-umm-g/update-mime-type)
    (errors/throw-service-errors
     :invalid-data [(format "Updating size is not supported for format [%s]" (:format concept))])))

(defn- update-additional-files
  "Update AdditionalFiles in given granule concept."
  [context concept value user-id]
  (condp = (mt/format-key (:format concept))
    :umm-json (update-umm-g-metadata context concept value user-id additional-file-umm-g/update-additional-files)
    (errors/throw-service-errors
     :invalid-data [(format "Updating AdditionalFiles is not supported for format [%s]" (:format concept))])))

(defn- modify-opendap-link*
  "For OPeNDAP links, there may be at most 2 urls, 1 cloud, 1 on-prem.
  Updates containing a url type that is already present on the concept
  will fail with an exception.
  Successful updates will return the updated concept."
  [context concept bulk-update-params target-field user-id xf]
  (let [{:keys [new-value]} bulk-update-params
        grouped-urls (opendap-util/validate-url new-value)
        updated-concept (if (= :Type target-field)
                          (xf context concept new-value user-id)
                          (xf context concept grouped-urls user-id))
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defn- update-online-url
  "Figure out which format to work on and update the 'from' URL to the 'to' URL."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :echo10 (online-resource-url-echo10/update-url concept (get bulk-update-params :new-value []) node-path-vector user-id)
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/update-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Updating %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- add-online-url
  "Figure out which format to work on and add the requested url to the
  specific granule."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :echo10 (online-resource-url-echo10/add-url concept (get bulk-update-params :new-value []) node-path-vector user-id)
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/append-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Adding %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- remove-online-url
  "Figure out which format to work on and remove the requested url from the
  specific granule."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :echo10 (online-resource-url-echo10/remove-url concept (get bulk-update-params :new-value []) node-path-vector user-id)
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/remove-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Removing %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- update-related-url
  "Figure out which format to work on and update the 'from' URL to the 'to' URL."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/update-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Updating %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- add-related-url
  "Figure out which format to work on and add the requested url to the
  specific granule."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/append-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Adding %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- remove-related-url
  "Figure out which format to work on and remove the requested url from the
  specific granule."
  [context concept bulk-update-params user-id node-path-vector]
  (condp = (mt/format-key (:format concept))
    :umm-json (update-umm-g-metadata context concept (get bulk-update-params :new-value []) user-id umm-g-util/remove-urls)
    (errors/throw-service-error
     :invalid-data
     (format "Removing %s is not supported for format [%s]" (last node-path-vector) (:format concept)))))

(defn- modify-s3-link*
  "Modify the S3Link data for the given concept with the provided URLs
  using the provided transform function.
  S3 links will be added to the existing concept. If a duplicate S3 link
  is provided it will be ignored."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [new-value]} bulk-update-params
        urls (s3-util/validate-url new-value)
        ;; invoke the appropriate transform
        updated-concept (xf context concept urls user-id)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defn- modify-checksum-size-format
  "Add or update the checksum, size, or format for the given concept with the provided values
  using the provided transform function."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [new-value]} bulk-update-params
        ;; invoke the appropriate transform - for checksum, there is only one option (update)
        updated-concept (xf context concept new-value user-id)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defn- modify-additional-files
  "Add or update the size, type, mimetype, and/or checksum value and algorithm for the given concept
   with the provided values using the provided transform function."
  [context concept bulk-update-params user-id xf]
  (let [{:keys [new-value]} bulk-update-params
        ;; invoke the appropriate transform - for checksum, there is only one option (update)
        updated-concept (xf context concept new-value user-id)
        {updated-metadata :metadata updated-format :format} updated-concept]
    (if-let [err-messages (:errors updated-metadata)]
      (errors/throw-service-errors :invalid-data err-messages)
      (-> concept
          (assoc :metadata updated-metadata)
          (assoc :format updated-format)
          (update :revision-id inc)
          (assoc :revision-date (time-keeper/now))
          (assoc :user-id user-id)))))

(defn update-granule-concept
  [context concept bulk-update-params user-id]
  (condp = (keyword (:event-type bulk-update-params))
    :update_field:onlineresourceurl (update-online-url context concept bulk-update-params user-id
                                                       [:OnlineResources :OnlineResource])
    :update_field:onlineaccessurl (update-online-url context concept bulk-update-params user-id
                                                     [:OnlineAccessURLs :OnlineAccessURL])
    :update_field:browseimageurl (update-online-url context concept bulk-update-params user-id
                                                    [:AssociatedBrowseImageUrls :ProviderBrowseUrl])
    :append_to_field:onlineresourceurl (add-online-url context concept bulk-update-params user-id
                                                       [:OnlineResources :OnlineResource])
    :append_to_field:onlineaccessurl (add-online-url context concept bulk-update-params user-id
                                                     [:OnlineAccessURLs :OnlineAccessURL])
    :append_to_field:browseimageurl (add-online-url context concept bulk-update-params user-id
                                                    [:AssociatedBrowseImageUrls :ProviderBrowseUrl])
    :remove_field:onlineresourceurl (remove-online-url context concept bulk-update-params user-id
                                                       [:OnlineResources :OnlineResource])
    :remove_field:onlineaccessurl (remove-online-url context concept bulk-update-params user-id
                                                     [:OnlineAccessURLs :OnlineAccessURL])
    :remove_field:browseimageurl (remove-online-url context concept bulk-update-params user-id
                                                    [:AssociatedBrowseImageUrls :ProviderBrowseUrl])
    :update_field:relatedurl (update-related-url context concept bulk-update-params user-id [:RelatedUrls])
    :append_to_field:relatedurl (add-related-url context concept bulk-update-params user-id [:RelatedUrls])
    :remove_field:relatedurl (remove-related-url context concept bulk-update-params user-id [:RelatedUrls])
    :update_field:additionalfile (modify-additional-files context concept bulk-update-params user-id update-additional-files)
    :update_field:checksum (modify-checksum-size-format context concept bulk-update-params user-id update-checksum)
    :update_field:size (modify-checksum-size-format context concept bulk-update-params user-id update-size)
    :update_field:format (modify-checksum-size-format context concept bulk-update-params user-id update-format)
    :update_field:mimetype (modify-checksum-size-format context concept bulk-update-params user-id update-mime-type)
    :update_field:s3link (modify-s3-link* context concept bulk-update-params user-id add-s3-url)
    :append_to_field:s3link (modify-s3-link* context concept bulk-update-params user-id append-s3-url)
    :update_field:opendaplink (modify-opendap-link* context concept bulk-update-params :URL user-id update-opendap-url)
    :append_to_field:opendaplink (modify-opendap-link* context concept bulk-update-params :URL user-id append-opendap-url)
    :update_type:opendaplink (modify-opendap-link* context concept bulk-update-params :Type user-id update-opendap-type)

    (invalid-event-type (:event-type bulk-update-params))))

;; TODO Step 9
(defn- update-granule-concept-and-status
  "Perform update for the granule concept and granule bulk update status. Throws error if concept cannot be updated."
  [context task-id concept granule-ur bulk-update-params user-id]
  (if-let [updated-concept (update-granule-concept context concept bulk-update-params user-id)]
    (do
      (ingest-service/save-granule context updated-concept) ;; TODO Why save one concept at a time to the DB? Why not do a bulk update in oracle?
      (data-granule-bulk-update/update-bulk-update-task-granule-status
       context task-id granule-ur bulk-update-service/updated-status ""))
    ;; when concept is nil this else statement triggers
    (data-granule-bulk-update/update-bulk-update-task-granule-status
     context task-id granule-ur bulk-update-service/skipped-status
     (format (str "Granule with granule-ur [%s] in task-id [%s] is not updated "
                  "because the metadata format [%s] is not supported.")
             granule-ur task-id (:format concept)))))

;; TODO Step 8
;; Example passed parameters
;; provider-id = "CDDIS"
;; task-id = 670092
;;  "bulk-update-params":{
;;    "event-type":"update_field:onlineaccessurl",
;;    "granule-ur":"gnss_data_highrate_2009_113_09d_00_darw113a15.09d.Z",
;;    "new-value":[{
;;                  "from":"https://cddis.nasa.gov/archive/gnss/data/highrate/2009/113/09d/00/darw113a15.09d.Z",
;;                  "to":"https://cddis.nasa.gov/archive/gnss/data/highrate/2009/113/darw1130.09d.txt"
;;                  },
;;                  {
;;                   "from":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2009/113/09d/00/darw113a15.09d.Z",
;;                    "to":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2009/113/darw1130.09d.txt"
;;                  }]},
;; user-id = "taylor.yates"
(defn handle-granule-bulk-update-event
  [context provider-id task-id bulk-update-params user-id]
  (let [{:keys [granule-ur]} bulk-update-params]
    (try
      ;; find concepts in database
      (if-let [concept (mdb/find-latest-concept
                        context {:provider-id provider-id :granule-ur granule-ur} :granule)]
        (if (:deleted concept)
          ;; if the concept found is deleted, update the task status as failed and log that it could not be updated
          ;; TODO do we want this? Why are we failing updates if the concept is deleted? And we are failing the entire task? How are tasks divided again? Per msg load?
          ;; Example of database bulk_update_gran_status
          ;; task_id, provider_id, granule_ur, instruction, updated_at, status, status_message
          ;; 544685,	CDDIS,	gnss_data_highrate_2007_281_07d_21_tash281v00.07d.Z,	{"event-type":"update_field:onlineaccessurl","granule-ur":"gnss_data_highrate_2007_281_07d_21_tash281v00.07d.Z","new-value":[{"from":"https://cddis.nasa.gov/archive/gnss/data/highrate/2007/281/07d/21/tash281v00.07d.Z","to":"https://cddis.nasa.gov/archive/gnss/data/highrate/2007/281/tash2810.07d.txt"},{"from":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2007/281/07d/21/tash281v00.07d.Z","to":"ftp://gdc.cddis.eosdis.nasa.gov/gnss/data/highrate/2007/281/tash2810.07d.txt"}]},	2024-04-20 01:20:55,	UPDATED,	(null)
          (data-granule-bulk-update/update-bulk-update-task-granule-status
           context task-id granule-ur bulk-update-service/failed-status
           (format (str "Granule with granule-ur [%s] on provider [%s] in task-id [%s] "
                        "is deleted. Can not be updated.")
                   granule-ur provider-id task-id))
          ;; granule found and not deleted, update the granule
          ;; TODO if concept is not found, this below code will still trigger... FIXME BUG? Do we want this behavior? Probably not.
          (update-granule-concept-and-status
           context task-id concept granule-ur bulk-update-params user-id))
        ;; granule not found
        ;; TODO why is this 'granule not found' logic here and not above the if statement? FIXME BUG
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
  (try
    (when-let [incomplete-tasks (data-granule-bulk-update/get-incomplete-granule-task-ids context)]
      (doseq [task incomplete-tasks]
        (when (data-granule-bulk-update/task-completed? context task)
          (data-granule-bulk-update/mark-task-complete context task))))
    (catch Exception e
      ;; not sure if this is the best way to handle this error, looks like a
      ;; bunch of noise during tests which has no impact.
      (comment println (.getMessage e)))))
