(ns cmr.search.results-handlers.umm-json-results-helper
  "Helper functions and definitions for handling common umm-json results format."
  (:require
   [clojure.string :as string]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]))

(def meta-fields
  "Defines the fields in elastic search we retrieve to populate the meta fields in the response."
  ["concept-id"
   "revision-id"
   "native-id"
   "user-id"
   "provider-id"
   "metadata-format"
   "revision-date"
   "deleted"])

(defn elastic-result->meta
  "Takes an elasticsearch result and returns a map of the meta fields for the response."
  [concept-type elastic-result]
  (let [{[concept-id] :concept-id
         [revision-id] :revision-id
         [native-id] :native-id
         [user-id] :user-id
         [provider-id] :provider-id
         [metadata-format] :metadata-format
         [revision-date] :revision-date
         [deleted] :deleted} (:fields elastic-result)
        revision-date (when revision-date (string/replace (str revision-date) #"\+0000" "Z"))]
    (util/remove-nil-keys
     {:concept-type concept-type
      :concept-id concept-id
      :revision-id revision-id
      :native-id native-id
      :user-id user-id
      :provider-id provider-id
      :format (mt/format->mime-type (keyword metadata-format))
      :revision-date revision-date
      :deleted deleted})))

(defn elastic-result->tuple
  "Returns a tuple of concept id and revision id from the elastic result of the given concept type."
  [concept-type elastic-result]
  [(get-in elastic-result [:fields :concept-id 0])
   (elastic-results/get-revision-id-from-elastic-result concept-type elastic-result)])
