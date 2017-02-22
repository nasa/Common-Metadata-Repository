(ns cmr.search.services.humanizers.humanizer-service
  "Provides functions for storing and retrieving humanizers"
  (:require
   [cheshire.core :as json]
   [cmr.common.concepts :as cc]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db :as mdb]
   [cmr.search.api.context-user-id-sids :as user-id-sids]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (or (util/lazy-get context :user-id) (user-id-sids/context->user-id context "Humanizer cannot be modified without a valid user token.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- humanizer-concept
  "Returns the set of humanizer instructions that can be persisted in metadata db."
  [context humanizer-json-str revision-id]
  {:concept-type :humanizer
   :native-id cc/humanizer-native-id
   :metadata humanizer-json-str
   :user-id (context->user-id context)
   :format mt/json
   :revision-id revision-id})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn- find-latest-humanizer-concept
  "Find the latest humanizer concept in the db"
  [context]
  (mdb/find-latest-concept context {:native-id cc/humanizer-native-id
                                    :latest true}
                           :humanizer))

(defn- concept->humanizers-map
  "Convert the concept to the humanizer map. If the data is just a list of humanizers (before adding
   community usage metrics), convert to a map before manipulting"
  [concept]
  (let [humanizers (json/parse-string (:metadata concept) true)]
    (if (map? humanizers)
      humanizers
      {:humanizers humanizers})))

(defn fetch-humanizer-concept
  "Fetches the latest version of a humanizer concept by humanizer-key"
  [context]
  (if-let [concept (find-latest-humanizer-concept context)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found "Humanizer has been deleted.")
      concept)
    (errors/throw-service-error :not-found "Humanizer does not exist.")))

(defn update-humanizers-metadata
  "Update and save the humanizers metadata function which includes humanizers and community usage.
   First check if a revision exists. If so, just update either the humanizers or community usage
   metrics. Increment the revision id manually to avoid race conditions if multiple updates are
   happening at the same time.
   Returns the concept id and revision id of the saved humanizer.

   key: What we are updating :humanizers or :community-usage-metrics
   data: The data to add to the file, as a clojure map"
  [context key data]
  {:pre [(or (= key :humanizers) (= key :community-usage-metrics))]}
  (if-let [latest-concept (find-latest-humanizer-concept context)]
    (let [humanizers (concept->humanizers-map latest-concept)
          humanizers (assoc humanizers key data) ; Overwrite just the data we are saving, not the whole file
          humanizer-concept (humanizer-concept context (json/generate-string humanizers) (inc (:revision-id latest-concept)))]
      (mdb/save-concept context humanizer-concept))
    (let [json (json/generate-string {key data}) ; No current revision exists, just write the data
          humanizer-concept (humanizer-concept context json 1)]
      (mdb/save-concept context humanizer-concept))))

(defn get-humanizers
  "Retrieves the set of humanizer instructions from metadata-db."
  [context]
  (:humanizers (json/decode (:metadata (fetch-humanizer-concept context)) true)))

(defn update-humanizers
  "Create/Update the humanizer instructions saving them as a humanizer revision in metadata db.
  Returns the concept id and revision id of the saved humanizer."
  [context humanizer-json-str]
  (let [humanizers (json/decode humanizer-json-str true)]
    (update-humanizers-metadata context :humanizers humanizers)))
