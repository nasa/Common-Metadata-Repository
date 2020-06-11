(ns cmr.indexer.data.concepts.subscription
  "Contains functions to parse and convert subscription concepts."
  (:require
   [clojure.string :as string]
   [cmr.acl.acl-fetcher :as acl-fetcher]
   [cmr.common.mime-types :as mt]
   [cmr.indexer.data.elasticsearch :as es]))

(defn- get-esm-permitted-group-ids-for-provider
  "EMAIL_SUBSCRIPTION_MANAGEMENT ACL grants permission on provider basis.
  Returns the groups ids (group guids, 'guest', 'registered') from EMAIL_SUBSCRIPTION_MANAGEMENT ACL
  that have the read permission for this provider.
  These group ids are indexed as part of subscription index, so that we know who can read the subscription.
  When searching for subscriptions, an acl search condition will be added to the query to match the user
  who does the search, with the permitted group ids in the index."
  [context provider-id]
  (->> (acl-fetcher/get-acls context [:provider-object])
       ;; Find only EMAIL_SUBSCRIPTION_MANAGEMENT ACL for this provider.
       (filter #(and (= "EMAIL_SUBSCRIPTION_MANAGEMENT" (get-in % [:provider-identity :target]))
                     (= provider-id (get-in % [:provider-identity :provider-id]))))
       ;; Get the permissions it grants
       (mapcat :group-permissions)
       ;; Find permissions that grant read
       (filter #(some (partial = "read") (:permissions %)))
       ;; Get the group guids or user type of those permissions
       (map #(or (:group-id %) (some-> % :user-type name)))
       distinct))

(defmethod es/parsed-concept->elastic-doc :subscription
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [subscription-name subscriber-id collection-concept-id]} extra-fields
        permitted-group-ids (get-esm-permitted-group-ids-for-provider context provider-id)
        doc-for-deleted
         {:concept-id concept-id
          :revision-id revision-id
          :deleted deleted
          :subscription-name subscription-name
          :subscription-name.lowercase (string/lower-case subscription-name)
          :subscriber-id subscriber-id
          :subscriber-id.lowercase (string/lower-case subscriber-id)
          :collection-concept-id collection-concept-id
          :collection-concept-id.lowercase (string/lower-case collection-concept-id)
          :provider-id provider-id
          :provider-id.lowercase (string/lower-case provider-id)
          :native-id native-id
          :native-id.lowercase (string/lower-case native-id)
          :user-id user-id
          :permitted-group-ids permitted-group-ids
          :revision-date revision-date}
         doc-for-non-deleted
          (merge {:metadata-format (name (mt/format-key format))} doc-for-deleted)]
    (if deleted
      doc-for-deleted
      doc-for-non-deleted)))
