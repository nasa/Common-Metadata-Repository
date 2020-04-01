(ns cmr.indexer.data.concepts.subscription
  "Contains functions to parse and convert subscription concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.indexer.data.elasticsearch :as es]))

(defmethod es/parsed-concept->elastic-doc :subscription
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [subscription-name subscriber-id collection-concept-id]} extra-fields]
    (if deleted
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
       :revision-date revision-date}
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
       :revision-date revision-date
       :metadata-format (name (mt/format-key format))})))
