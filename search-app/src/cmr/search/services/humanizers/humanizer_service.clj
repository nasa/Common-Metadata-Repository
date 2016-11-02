(ns cmr.search.services.humanizers.humanizer-service
  "Provides functions for storing and retrieving humanizers"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.concepts :as cc]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.search.services.humanizers.humanizer-json-schema-validation :as hv]
            [cheshire.core :as json]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized "Humanizer cannot be modified without a valid user token.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- humanizer-concept
  "Returns the set of humanizer instructions that can be persisted in metadata db."
  [context humanizer-json-str]
  {:concept-type :humanizer
   :native-id cc/humanizer-native-id
   :metadata humanizer-json-str
   :user-id (context->user-id context)
   :format mt/json})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn- fetch-humanizer-concept
  "Fetches the latest version of a humanizer concept by humanizer-key"
  [context]
  (if-let [concept (mdb/find-latest-concept context
                                            {:native-id cc/humanizer-native-id
                                             :latest true}
                                            :humanizer)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found "Humanizer has been deleted.")
      concept)
    (errors/throw-service-error :not-found "Humanizer does not exist.")))

(defn get-humanizers
  "Retrieves the set of humanizer instructions from metadata-db."
  [context]
  (json/decode (:metadata (fetch-humanizer-concept context)) true))

(defn update-humanizers
  "Create/Update the humanizer instructions saving them as a humanizer revision in metadata db.
  Returns the concept id and revision id of the saved humanizer."
  [context humanizer-json-str]
  (hv/validate-humanizer-json humanizer-json-str)
  (let [humanizer-concept (humanizer-concept context humanizer-json-str)]
      (mdb/save-concept context humanizer-concept)))
