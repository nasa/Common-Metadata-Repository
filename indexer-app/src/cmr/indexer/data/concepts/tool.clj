(ns cmr.indexer.data.concepts.tool
  "Contains functions to parse and convert tool and tool association concepts."
  (:require
   [clojure.string :as string]
   [cmr.common.concepts :as concepts]
   [cmr.common.log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.indexer.data.concept-parser :as concept-parser]
   [cmr.indexer.data.concepts.keyword-util :as keyword-util]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.transmit.metadata-db :as mdb]))

(defmethod es/parsed-concept->elastic-doc :tool
  [context concept parsed-concept]
  (let [{:keys [concept-id revision-id deleted provider-id native-id user-id
                revision-date format extra-fields]} concept
        {:keys [tool-name]} extra-fields
        long-name (:LongName parsed-concept)
        schema-keys [:LongName
                     :Name
                     :Version
                     :AncillaryKeywords
                     :ContactGroups
                     :ContactPersons
                     :URL
                     :RelatedURLs
                     :ToolKeywords
                     :Organizations]
        keyword-values (keyword-util/concept-keys->keyword-text
                        parsed-concept schema-keys)
        doc-for-deleted 
         {:concept-id concept-id
          :revision-id revision-id
          :deleted deleted
          :tool-name tool-name
          :tool-name.lowercase (string/lower-case tool-name)
          :provider-id provider-id
          :provider-id.lowercase (string/lower-case provider-id)
          :native-id native-id
          :native-id.lowercase (string/lower-case native-id)
          :keyword keyword-values
          :user-id user-id
          :revision-date revision-date}]
    (if deleted
      doc-for-deleted
      (assoc doc-for-deleted :metadata-format (name (mt/format-key format)) 
                             :long-name long-name
                             :long-name.lowercase (string/lower-case long-name)))))
