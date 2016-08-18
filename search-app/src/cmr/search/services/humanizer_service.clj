(ns cmr.search.services.humanizer-service
  "Provides functions for storing and retrieving humanizers"
  (:require [cmr.transmit.metadata-db :as mdb]
            [cmr.common.mime-types :as mt]
            [cmr.common.util :as util]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.concepts :as cc]
            [cmr.transmit.echo.tokens :as tokens]
            [cmr.common.services.errors :as errors]
            [cmr.common.services.messages :as cmsg]
            [cmr.search.services.humanizer.humanizer-json-schema-validation :as hv]
            [cmr.common.concepts :as concepts]
            [cmr.search.services.json-parameters.conversion :as jp]
            [cmr.common-app.services.search.query-execution :as qe]
            [cmr.search.services.query-service :as query-service]
            [cmr.metadata-db.services.concept-service :as mdb-cs]
            [cmr.metadata-db.services.search-service :as mdb-ss]
            [clojure.string :as str]
            [cheshire.core :as json]
            [clojure.edn :as edn]))

(defn- context->user-id
  "Returns user id of the token in the context. Throws an error if no token is provided"
  [context]
  (if-let [token (:token context)]
    (tokens/get-user-id context (:token context))
    (errors/throw-service-error :unauthorized "Humanizer cannot be modified without a valid user token.")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Metadata DB Concept Map Manipulation

(defn- humanizer-concept
  "Returns the humanizer that can be persisted in metadata db."
  [context humanizer-json-str]
  {:concept-type :humanizer
   :native-id cc/humanizer-native-id
   :metadata humanizer-json-str
   :user-id (context->user-id context)
   :format mt/json})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn update-humanizer
  "Create/Update the humanizer saving it as a revision in metadata db.
  Returns the concept id and revision id of the saved humanizer."
  [context humanizer-json-str]
  (hv/validate-humanizer-json humanizer-json-str)
  (let [humanizer-concept (humanizer-concept context humanizer-json-str)]
    (mdb/save-concept context humanizer-concept)))

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

(defn get-humanizer
  "Retrieves the humanizer."
  [context]
  (json/parse-string (:metadata (fetch-humanizer-concept context))))

