(ns cmr.access-control.data.elasticsearch
  "Functions related to indexing access control data in Elasticsearch."
  (:require
   [cmr.access-control.data.access-control-index :as access-control-index]
   [cmr.access-control.data.bulk :as cmr-bulk]
   [cmr.common.concepts :as cs]
   [cmr.common.log :refer [info error]]
   [cmr.common.services.errors :as errors]
   [cmr.elastic-utils.es-helper :as es-helper]))

(def MAX_BULK_OPERATIONS_PER_REQUEST
  "The maximum number of operations to batch in a single request"
  100)

(defn- concept->type
  "Returns concept type for the given concept"
  [concept]
  (cs/concept-id->type (:concept-id concept)))

(defmulti parsed-concept->elastic-doc
  "Returns elastic json that can be used to insert into Elasticsearch for the given concept"
  (fn [context concept parsed-concept]
    (cs/concept-id->type (:concept-id concept))))

(defmethod parsed-concept->elastic-doc :acl
  [_ concept _]
  (if (:deleted concept)
    concept
    (access-control-index/acl-concept-map->elastic-doc concept)))

(defmethod parsed-concept->elastic-doc :access-group
  [_ concept _]
  (if (:deleted concept)
    concept
    (access-control-index/group-concept-map->elastic-doc concept)))

(defn- concept->bulk-elastic-docs
  "Converts a concept map into an elastic document suitable for bulk indexing."
  [context concept {:keys [all-revisions-index?] :as options}]
  (try
    (let [{:keys [concept-id revision-id]} concept
          type (concept->type concept)
          elastic-version (:revision-id concept)
          index-name (access-control-index/concept-type->index-name type)
          elastic-doc (parsed-concept->elastic-doc context concept concept)
          ;; "only index the document if the given version is equal or higher than
          ;; the version of the stored document."
          version-type "external_gte"
          elastic-doc (merge elastic-doc
                             {:_id concept-id
                              :version elastic-version
                              :version_type version-type})]
      (assoc elastic-doc :_index index-name))

    (catch Throwable e
      (error e (str "Skipping failed catalog item. Exception trying to convert concept to elastic doc:"
                    (pr-str concept))))))

(defn prepare-batch
  "Convert a batch of concepts into elastic docs for bulk indexing."
  [context concept-batch options]
  (doall
   (->> concept-batch
        (pmap #(concept->bulk-elastic-docs context % options))
        ;; Remove nils because some concepts may fail with an exception and return nil.
        (remove nil?)
        flatten)))

(defn bulk-index-documents
  "Save a batch of documents in Elasticsearch."
  [context docs]
  (doseq [docs-batch (partition-all MAX_BULK_OPERATIONS_PER_REQUEST docs)]
    (let [bulk-operations (cmr-bulk/create-bulk-index-operations docs-batch)
          conn (get-in context [:system :db :conn]) ;; Why db?
          response (es-helper/bulk conn bulk-operations)
          ;; we don't care about version conflicts or deletes that aren't found
          bad-errors (some (fn [item]
                             (let [status (if (:index item)
                                            (get-in item [:index :status])
                                            (get-in item [:delete :status]))]
                               (and (> status 399)
                                    (not= 409 status)
                                    (not= 404 status))))
                           (:items response))]
      (when bad-errors
        (errors/internal-error! (format "Bulk indexing failed with response %s" response))))))
