(ns cmr.metadata-db.services.search-service
  "Contains functions for retrieving concepts using parameter search"
  (:require
   [clojure.set :as set]
   [cmr.common.concepts :as cc]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.util :as util]
   [cmr.metadata-db.data.concepts :as c]
   [cmr.metadata-db.services.messages :as msg]
   [cmr.metadata-db.services.provider-service :as provider-service]
   [cmr.metadata-db.services.util :as db-util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validations for find concepts

(def default-supported-find-parameters
  "The set of find parameters supported by most concepts."
  #{:concept-id :provider-id :native-id})

(def supported-find-parameters
  "Map of concept-types to sets of parameters supported by find for each type."
  (merge
   {:collection #{:concept-id :provider-id :entry-title :entry-id :short-name :version-id :native-id}
    :tag #{:concept-id :native-id}
    :tag-association #{:concept-id :native-id :associated-concept-id :associated-revision-id :tag-key}
    :service #{:provider-id :concept-id :native-id}
    :tool #{:provider-id :concept-id :native-id}
    :access-group default-supported-find-parameters
    :acl default-supported-find-parameters
    :humanizer #{:concept-id :native-id}
    :subscription #{:provider-id :concept-id :native-id :collection-concept-id :subscriber-id :normalized-query
                    :subscription-type}
    :variable #{:provider-id :concept-id :native-id}
    :variable-association #{:concept-id :native-id :associated-concept-id :associated-revision-id
                            :variable-concept-id}
    :service-association #{:concept-id :native-id :associated-concept-id :associated-revision-id
                           :service-concept-id}
    :tool-association #{:concept-id :native-id :associated-concept-id :associated-revision-id
                        :tool-concept-id}
    :generic-association #{:concept-id :native-id :associated-concept-id :associated-revision-id
                          :source-concept-identifier :source-revision-id :association-type}}
   (zipmap (cc/get-generic-concept-types-array)
           (repeat #{:concept-id :provider-id :native-id}))))

(def granule-supported-parameter-combinations
  "Supported search parameter combination sets for granule find. This does not include flags
  like 'exclude-metadata' or 'latest'."
  #{#{:provider-id :granule-ur}
    #{:provider-id :native-id}})

(def find-concepts-flags
  "Flags that affect find concepts but aren't part of the actual search parameters."
  #{:exclude-metadata :latest})

(defmulti supported-parameter-combinations-validation
  "Validates the find parameters for a concept type."
  (fn [params]
    (keyword (:concept-type params))))

;; For performance reasons we only support finding granules using combinations of indexed fields,
;; so we validate the parameters for granules separately from other concept types that allow
;; finds using single fields.
(defmethod supported-parameter-combinations-validation :granule
  [{:keys [concept-type] :as params}]
  (let [params (dissoc params :concept-type)
        search-params (set (keys (apply dissoc params find-concepts-flags)))]
    (when-not (contains? granule-supported-parameter-combinations search-params)
      [(msg/find-not-supported-combination concept-type (keys params))])))

(defmethod supported-parameter-combinations-validation :default
  [params]
  (let [concept-type (:concept-type params)
        params (dissoc params :concept-type)
        supported-params (set/union (get supported-find-parameters concept-type)
                                    find-concepts-flags)]
    (when-let [unsupported-params (seq (set/difference (set (keys params))
                                                       supported-params))]
      [(msg/find-not-supported concept-type unsupported-params)])))

(def find-params-validation
  "Validates parameters for finding a concept."
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
    (let [providers (provider-service/get-providers context)]
      (if (= :subscription (:concept-type params))
        (concat [{:provider-id "CMR"}] providers)
        providers))))

(defn- find-cmr-concepts
  "Find tags or tag associations with specific parameters"
  [context params]
  (when (or (nil? (:provider-id params))
            (= "CMR" (:provider-id params)))
    (let [db (db-util/context->db context)
          latest-only? (or (true? (:latest params))
                           (= "true" (:latest params)))
          params (dissoc params :latest)]
      (if latest-only?
        (c/find-latest-concepts db {:provider-id "CMR"} params)
        (c/find-concepts db [{:provider-id "CMR"}] params)))))

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
  (cond
    (contains? #{:tag
                 :tag-association
                 :acl
                 :humanizer
                 :variable-association
                 :service-association
                 :tool-association
                 :generic-association}
               (:concept-type params))
    (find-cmr-concepts context params)

    (= :access-group (:concept-type params))
    (concat
     (find-cmr-concepts context params)
     (find-provider-concepts context params))

    :else
    (find-provider-concepts context params)))
