(ns cmr.indexer.data.concepts.subscription
  "Contains functions to parse and convert subscription concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.elasticsearch :as es]))

(defmethod es/parsed-concept->elastic-doc :subscription
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [subscription-name subscriber-id collection-concept-id]} extra-fields
        type (:Type parsed-concept)
        doc-for-deleted
         {:concept-id concept-id
          :revision-id revision-id
          :deleted deleted
          :subscription-name subscription-name
          :subscription-name-lowercase (util/safe-lowercase subscription-name)
          :subscriber-id subscriber-id
          :subscriber-id-lowercase (util/safe-lowercase subscriber-id)
          :collection-concept-id collection-concept-id
          :collection-concept-id-lowercase (util/safe-lowercase collection-concept-id)
          :provider-id provider-id
          :provider-id-lowercase (util/safe-lowercase provider-id)
          :native-id native-id
          :native-id-lowercase (util/safe-lowercase native-id)
          :user-id user-id
          :subscription-type type
          :subscription-type-lowercase (util/safe-lowercase type)
          :revision-date revision-date}]
    (if deleted
      doc-for-deleted
      (assoc doc-for-deleted :metadata-format (name (mt/format-key format))))))