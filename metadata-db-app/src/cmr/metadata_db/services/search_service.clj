(ns cmr.metadata-db.services.search-service
  "Contains functions for retrieving concepts using parameter search"
  (:require [cmr.metadata-db.data.concepts :as c]
            [cmr.metadata-db.services.util :as db-util]
            [cmr.metadata-db.services.provider-service :as provider-service]
            [cmr.metadata-db.services.messages :as msg]
            [clojure.set :as set]
            [cmr.common.log :refer (debug info warn error)]
            [cmr.common.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations for find concepts

(def supported-collection-parameters
  "Set of parameters supported by find for collections"
  #{:concept-id :provider-id :entry-title :entry-id :short-name :version-id})

(def granule-supported-parameter-combinations
  "Supported search parameter combination sets for granule find. This does not include flags
  like 'exclude-metadata' or 'latest'."
  #{#{:provider-id :granule-ur}
    #{:provider-id :native-id}})

(def supported-tag-parameters
  "Set of parameters supported by find for collections"
  ;; We can add some search parameters later, for now we don't see a need for any.
  #{})

(def find-concepts-flags
  "Flags that affect find concepts but aren't part of the actual search parameters."
  #{:exclude-metadata :latest})

(defmulti supported-parameter-combinations-validation
  "Validates the find parameters for a concept type"
  (fn [params]
    (:concept-type params)))

(defmethod supported-parameter-combinations-validation :collection
  [params]
  (let [params (dissoc params :concept-type)
        supported-params (set/union supported-collection-parameters find-concepts-flags)]
    (when-let [unsupported-params (seq (set/difference (set (keys params))
                                                       supported-params))]
      [(msg/find-not-supported :collection unsupported-params)])))

(defmethod supported-parameter-combinations-validation :granule
  [{:keys [concept-type] :as params}]
  (let [params (dissoc params :concept-type)
        search-params (set (keys (apply dissoc params find-concepts-flags)))]
    (when-not (contains? granule-supported-parameter-combinations search-params)
      [(msg/find-not-supported-combination concept-type (keys params))])))

(defmethod supported-parameter-combinations-validation :tag
  [params]
  (let [params (dissoc params :concept-type)
        supported-params (set/union supported-tag-parameters find-concepts-flags)]
    (when-let [unsupported-params (seq (set/difference (set (keys params))
                                                       supported-params))]
      [(msg/find-not-supported :tag unsupported-params)])))

(defmethod supported-parameter-combinations-validation :default
  [{:keys [concept-type] :as params}]
  [(msg/find-not-supported-combination concept-type (keys (dissoc params :concept-type)))])

(def find-params-validation
  "Validates parameters for finding a concept"
  (util/compose-validations [supported-parameter-combinations-validation]))

(def validate-find-params
  "Validates find parameters. Throws an error if invalid."
  (util/build-validator :bad-request find-params-validation))

(defn- find-providers-for-params
  "Find providers that match the given parameters. If no providers are specified we return all
  providers as possible providers that could match the parameters."
  [context params]
  (if-let [provider-id (:provider-id params)]
    (when-let [provider (provider-service/get-provider-by-id context provider-id)]
      [provider])
    (provider-service/get-providers context)))

(defn- find-tags
  "Find tags with specific parameters"
  [context params]
  (let [db (db-util/context->db context)
        latest-only? (or (true? (:latest params))
                         (= "true" (:latest params)))
        params (dissoc params :latest)]
    (if latest-only?
      (c/find-latest-concepts db {:provider-id "CMR"} params)
      (c/find-concepts db [{:provider-id "CMR"}] params))))

(defn- find-provider-concepts
  "Find concepts with specific parameters"
  [context params]
  (let [db (db-util/context->db context)
        latest-only? (or (true? (:latest params))
                         (= "true" (:latest params)))
        params (dissoc params :latest)
        providers (find-providers-for-params context params)]
    (when (seq providers)
      (if latest-only?
        (mapcat #(c/find-latest-concepts db % params) providers)
        (c/find-concepts db providers params)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service methods for finding concepts

(defn find-concepts
  "Find concepts with specific parameters"
  [context params]
  (validate-find-params params)
  (if (= :tag (:concept-type params))
    (find-tags context params)
    (find-provider-concepts context params)))

(defn find-concept
  "Returns nil or exactly one concept matching the params.
  Throws exception if more than one concept matches the given params."
  [context params]
  (let [concepts (find-concepts context params)]
    (condp = (count concepts)
      0 nil
      1 (first concepts)
      (throw (IllegalArgumentException. "Query returned more than one concept.")))))
