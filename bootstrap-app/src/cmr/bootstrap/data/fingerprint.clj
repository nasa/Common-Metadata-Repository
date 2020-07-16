(ns cmr.bootstrap.data.fingerprint
  "Functions to support updating variable fingerprint."
  (:require
    [cmr.bootstrap.embedded-system-helper :as helper]
    [cmr.common.concepts :as concepts]
    [cmr.common.log :refer (debug info)]
    [cmr.common.services.errors :as errors]
    [cmr.metadata-db.data.concepts :as db]
    [cmr.umm-spec.fingerprint-util :as fingerprint-util]))

(defn- fingerprint-variable
  "Update the fingerprint of the given variable if necessary."
  [db provider variable]
  (let [{:keys [concept-id revision-id deleted metadata]} variable]
    (when-not deleted
      (let [old-fingerprint (get-in variable [:extra-fields :fingerprint])
            new-fingerprint (fingerprint-util/get-variable-fingerprint metadata)]
        (when (not= old-fingerprint new-fingerprint)
          (db/save-concept db
                           provider
                           (-> variable
                               (assoc :revision-id (inc revision-id))
                               (assoc-in [:extra-fields :fingerprint] new-fingerprint)
                               (dissoc :revision-date)))
          (debug (format "Updated fingerprint for concept-id: %s" concept-id)))))))

(defn- fingerprint-variable-batch
  "Update the fingerprints of variables in the given variable batch if necessary."
  [db provider variable-batch]
  (run! #(fingerprint-variable db provider %) variable-batch))

(def ^:private find-variables-sql-part1
  "Defines the beginning part of the string to construct find variables sql statement."
  (str "select a.* from metadata_db.cmr_variables a, "
       "(select concept_id, max(revision_id) rid from metadata_db.cmr_variables "))

(def ^:private find-variables-sql-part2
  "Defines the ending part of the string to construct find variables sql statement."
  (str "group by concept_id) b"
       " where a.concept_id = b.concept_id and a.revision_id = b.rid and a.deleted = 0"))

(defn- find-variables-sql
  "Returns the sql statement to find the latest variable concept revisions that are not deleted."
  ([]
   (str find-variables-sql-part1 find-variables-sql-part2))
  ([provider-id]
   (format "%s where provider_id='%s' %s"
           find-variables-sql-part1 provider-id find-variables-sql-part2)))

(defn- fingerprint-by-provider
  "Update the fingerprints of variables of the given provider if necessary."
  [system provider]
  (info "Updating fingerprints of variables for provider" (:provider-id provider))
  (let [db (helper/get-metadata-db-db system)
        {:keys [provider-id]} provider
        params {:concept-type :variable
                :provider-id provider-id}
        variable-batches (db/find-concepts-in-batches-with-stmt db
                                                                provider
                                                                params
                                                                (find-variables-sql provider-id)
                                                                (:db-batch-size system))
        num-variables (reduce (fn [num batch]
                                (fingerprint-variable-batch db provider batch)
                                (+ num (count batch)))
                              0
                              variable-batches)]
    (info (format "Updated fingerprints of %d variable(s) for provider %s"
                  num-variables provider-id))
    (info (format "Updating fingerprints of variables for provider %s completed." provider-id))))

(defn- fingerprint-by-provider-id
  "Update the fingerprints of variables for the given provider id if necessary."
  [system provider-id]
  (if-let [provider (helper/get-provider system provider-id)]
    (fingerprint-by-provider system provider)
    (errors/throw-service-error
     :invalid-data (format "Provider [%s] does not exist" provider-id))))

(defn- fingerprint-all-variables
  "Update the fingerprints of variables of the given provider if necessary."
  [system]
  (info "Updating fingerprints for all variables.")
  (doseq [provider (helper/get-providers system)]
    (fingerprint-by-provider system provider))
  (info "Updating fingerprints for all variables completed."))

(defn fingerprint-variables
  "Update the fingerprints of variables specified by the given params if necessary."
  ([system]
   (fingerprint-all-variables system))
  ([system provider-id]
   (fingerprint-by-provider-id system provider-id)))

(defn fingerprint-by-id
  "Update the fingerprint of the given variable specified by its concept id if necessary."
  [system concept-id]
  (let [db (helper/get-metadata-db-db system)
        provider-id (concepts/concept-id->provider-id concept-id)
        provider (helper/get-provider system provider-id)
        variable (db/get-concept db :variable provider concept-id)]
    (if variable
      (fingerprint-variable db provider variable)
      (errors/throw-service-error
       :invalid-data (format "Variable with concept-id [%s] does not exist" concept-id)))))
