(ns cmr.access-control.services.acl-service
  (:require [clojure.string :as str]
            [cmr.access-control.services.acl-service-messages :as msg]
            [cmr.common.log :refer [info]]
            [cmr.common.util :as u]
            [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as errors]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.transmit.metadata-db2 :as mdb]
            [cmr.common-app.services.search :as cs]
            [cmr.common-app.services.search.params :as cp]
            [cmr.common-app.services.search.parameter-validation :as cpv]
            [cmr.common-app.services.search.query-model :as common-qm]
            [clojure.edn :as edn]
            [clojure.set :as set]
            [cmr.common.concepts :as concepts]
            [cmr.access-control.services.group-service :as groups]
            [cmr.transmit.metadata-db :as mdb1]
            [cmr.umm-spec.legacy :as umm-legacy]
            [cmr.acl.core :as acl]
            [cmr.umm.acl-matchers :as acl-matchers]
            [cmr.common.util :as util]))

(def acl-provider-id
  "The provider ID for all ACLs. Since ACLs are not owned by individual
  providers, they fall under the CMR system provider ID."
  "CMR")

(defn acl-identity
  "Returns a string value representing the ACL's identity field."
  [acl]
  (str/lower-case
    (let [{:keys [system-identity provider-identity single-instance-identity catalog-item-identity]} acl]
      (cond
        system-identity          (str "system:" (:target system-identity))
        single-instance-identity (format "single-instance:%s:%s"
                                         (:target-id single-instance-identity)
                                         (:target single-instance-identity))
        provider-identity        (format "provider:%s:%s"
                                         (:provider-id provider-identity)
                                         (:target provider-identity))
        catalog-item-identity    (format "catalog-item:%s:%s"
                                         (:provider-id catalog-item-identity)
                                         (:name catalog-item-identity))
        :else                    (errors/throw-service-error
                                   :bad-request "malformed ACL")))))

(defn- fetch-acl-concept
  "Fetches the latest version of ACL concept by concept id. Handles unknown concept ids by
  throwing a service error."
  [context concept-id]
  (let [{:keys [concept-type provider-id]} (concepts/parse-concept-id concept-id)]
    (when (not= :acl concept-type)
      (errors/throw-service-error :bad-request (msg/bad-acl-concept-id concept-id))))

  (if-let [concept (mdb/get-latest-concept context concept-id false)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found (msg/acl-deleted concept-id))
      concept)
    (errors/throw-service-error :not-found (msg/acl-does-not-exist concept-id))))

(defn- acl->base-concept
  "Returns a basic concept map for the given request context and ACL map."
  [context acl]
  {:concept-type :acl
   :metadata (pr-str acl)
   :format mt/edn
   :provider-id acl-provider-id
   :user-id (tokens/get-user-id context (:token context))
   ;; ACL-specific fields
   :extra-fields {:acl-identity (acl-identity acl)}})

(defn create-acl
  "Save a new ACL to Metadata DB. Returns map with concept and revision id of created acl."
  [context acl]
  (mdb/save-concept context (merge (acl->base-concept context acl)
                                   {:revision-id 1
                                    :native-id (str (java.util.UUID/randomUUID))})))

(defn update-acl
  "Update the ACL with the given concept-id in Metadata DB. Returns map with concept and revision id of updated acl."
  [context concept-id acl]
  ;; This fetch acl call also validates if the ACL with the concept id does not exist or is deleted
  (let [existing-concept (fetch-acl-concept context concept-id)
        existing-legacy-guid (:legacy-guid (edn/read-string (:metadata existing-concept)))
        legacy-guid (:legacy-guid acl)]
    (when-not (= existing-legacy-guid legacy-guid)
      (errors/throw-service-error
        :invalid-data (format "ACL legacy guid cannot be updated, was [%s] and now [%s]"
                              existing-legacy-guid legacy-guid)))
    (mdb/save-concept context (merge (acl->base-concept context acl)
                                     {:concept-id concept-id
                                      :native-id (:native-id existing-concept)}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Search functions

(defmethod cpv/params-config :acl
  [_]
  (cpv/merge-params-config
    cpv/basic-params-config
    {:single-value #{}
     :multiple-value #{:permitted-group}
     :always-case-sensitive #{}
     :disallow-pattern #{}
     :allow-or #{}}))

(defmethod cpv/valid-parameter-options :acl
  [_]
  {:permitted-group cpv/string-param-options})

(defn- valid-permitted-group?
  "Returns true if the given permitted group is valid, i.e. guest, registered or conforms to
  access group id format."
  [group]
  (or (.equalsIgnoreCase "guest" group)
      (.equalsIgnoreCase "registered" group)
      (some? (re-find #"[Aa][Gg]\d+-.+" group))))

(defn- permitted-group-validation
  "Validates permitted group parameters."
  [context params]
  (let [permitted-groups (:permitted-group params)
        permitted-groups (if (sequential? permitted-groups)
                           permitted-groups
                           (when permitted-groups [permitted-groups]))]
    (when-let [invalid-groups (seq (remove valid-permitted-group? permitted-groups))]
      [(format "Parameter permitted_group has invalid values [%s]. Only 'guest', 'registered' or a group concept id can be specified."
               (str/join ", " invalid-groups))])))

(defn validate-acl-search-params
  "Validates the parameters for an ACL search. Returns the parameters or throws an error if invalid."
  [context params]
  (let [[safe-params type-errors] (cpv/apply-type-validations
                                    params
                                    [(partial cpv/validate-map [:options])
                                     (partial cpv/validate-map [:options :permitted-group])])]
    (cpv/validate-parameters
      :acl safe-params
      (concat cpv/common-validations
              [permitted-group-validation])
      type-errors))
  params)

(defmethod common-qm/default-sort-keys :acl
  [_]
  [{:field :display-name :order :asc}])

(defmethod cp/param-mappings :acl
  [_]
  {:permitted-group :string})

(defn search-for-acls
  [context params]
  (let [[query-creation-time query] (u/time-execution
                                      (->> params
                                           cp/sanitize-params
                                           (validate-acl-search-params :acl)
                                           (cp/parse-parameter-query :acl)))
        [find-concepts-time results] (u/time-execution
                                       (cs/find-concepts context :acl query))
        total-took (+ query-creation-time find-concepts-time)]
    (info (format "Found %d acls in %d ms in format %s with params %s."
                  (:hits results) total-took (common-qm/base-result-format query) (pr-str params)))
    (assoc results :took total-took)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Member functions

(defn get-acl
  "Returns the parsed metadata of the latest revision of the ACL concept by id."
  [context concept-id]
  (edn/read-string (:metadata (fetch-acl-concept context concept-id))))

(defn echo-style-acl
  "Returns acl with the older ECHO-style keywords for consumption in utility functions from other parts of the CMR."
  [acl]
  (-> acl
      (set/rename-keys {:system-identity :system-object-identity
                                :provider-identity :provider-object-identity
                                :group-permissions :aces})
      (util/update-in-each [:aces] update-in [:user-type] keyword)
      (util/update-in-each [:aces] set/rename-keys {:group-id :group-guid})))

;; catalog item identities are only ever relevant to read and order
;; provider identities are releveant to update and delete

(defn- acls-granting-permissions
  "Returns the set of permission keywords (:read, :update, :order, or :delete) granted on concept
   to the seq of group guids by seq of acls."
  [concept group-ids acls]
  (let [sids (concat [:guest :registered] group-ids)
        grants-permission? (fn [acl permission]
                             (acl/acl-matches-sids-and-permission? sids permission acl))
        matches-collection? (fn [acl]
                              (acl-matchers/coll-applicable-acl? (:provider-id concept) concept acl))
        ;; soon...
        matches-granule? (constantly false)
        matches-concept? (fn [acl]
                           (condp = (:concept-type concept)
                             :collection (matches-collection? acl)
                             :granule (matches-granule? acl)))
        matches-provider? (fn [acl]
                            (when-let [acl-provider-id (-> acl :provider-object-identity :provider-id)]
                              (= acl-provider-id (:provider-id concept))))]
    (set
      (concat
        (for [permission [:update :delete]
              :when (boolean
                      (some #(and (grants-permission? % (name permission))
                                  (matches-provider? %))
                            acls))]
          permission)
        (for [permission [:read :order]
              :when (boolean
                      (some #(and (grants-permission? % (name permission))
                                  (matches-concept? %))
                            acls))]
          permission)))))

(defn get-granted-permissions
  [context username concept-ids]
  (let [concepts (map (partial mdb/get-latest-concept context) concept-ids)
        groups (-> (groups/search-for-groups context {:member username}) :results :items)
        group-ids (set (map :concept_id groups))
        ;; fetch and parse all ACLs lazily
        acls (for [batch (mdb1/find-in-batches context :acl 1000 {:latest true})
                   acl-concept batch]
               (echo-style-acl (edn/read-string (:metadata acl-concept))))]
    (into {}
          (for [concept concepts]
            [(:concept-id concept) (acls-granting-permissions concept group-ids acls)]))))
