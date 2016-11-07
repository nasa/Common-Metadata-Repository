(ns cmr.search.services.humanizers.humanizer-service
  "Provides functions for storing and retrieving humanizers"
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.string :as str]
   [cmr.common.concepts :as cc]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.mime-types :as mt]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.humanizers.humanizer-json-schema-validation :as hv]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.transmit.metadata-db :as mdb]))

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

(defn- find-latest-humanizer-concept
  "Find the latest humanizer concept in the db"
  [context]
  (mdb/find-latest-concept context {:native-id cc/humanizer-native-id
                                    :latest true}
                           :humanizer))

(defn- fetch-humanizer-concept
  "Fetches the latest version of a humanizer concept by humanizer-key"
  [context]
  (if-let [concept (find-latest-humanizer-concept context)]
    (if (:deleted concept)
      (errors/throw-service-error :not-found "Humanizer has been deleted.")
      concept)
    (errors/throw-service-error :not-found "Humanizer does not exist.")))

(defn- get-community-usage-columns
  "The community usage csv has many columns. Get the indices of the columns we want to store."
  [csv-header]
  {:product-col (.indexOf csv-header "Product")
   :version-col (.indexOf csv-header "Version")
   :hosts-col (.indexOf csv-header "Hosts")})

(defn- read-csv-column
  "Read a column from the csv, if the column is exists. Otherwise, return nil."
  [csv-line col]
  (when (>= col 0)
    (nth csv-line col)))

(defn- csv-entry->community-usage-metric
  "Convert a line in the csv file to a community usage metric. Only storing short-name (product),
   version (can be 'N/A' or a version number), and access-count (hosts)"
  [csv-line product-col version-col hosts-col]
  {:short-name (read-csv-column csv-line product-col)
   :version (read-csv-column csv-line version-col)
   :access-count (when-let [access-count (read-csv-column csv-line hosts-col)]
                   (read-string access-count))})

(defn- community-usage-csv->community-usage-metrics
  "Convert the community usage csv to a list of community usage metrics to save"
  [community-usage-csv]
  (when-let [csv-lines (seq (csv/read-csv community-usage-csv))]
    (let [{:keys [product-col version-col hosts-col]} (get-community-usage-columns (first csv-lines))]
      (seq (map #(csv-entry->community-usage-metric % product-col version-col hosts-col) (rest csv-lines))))))

(defn- aggregate-usage-metrics
 "Combine access counts for entries with the same short-name and version number."
 [metrics]
 (let [name-version-groups (group-by (juxt :short-name :version) metrics)] ; Group by short-name and version
   ;; name-version-groups is map of [short-name, version] [entries that match short-name/version]
   ;; The first entry in each list has the short-name and version we want so just add up the access-counts
   ;; in the rest and add that to the first entry to make the access-counts right
   (map #(util/remove-nil-keys (assoc (first %) :access-count (reduce + (map :access-count %))))
        (vals name-version-groups))))

(defn- save-humanizers
  "Save the humanizers, which includes both community usage metrics and humanizers."
  ([context humanizer-json]
   (save-humanizers context humanizer-json nil))
  ([context humanizer-json revision-id]
   (hv/validate-humanizer-json humanizer-json)
   (let [humanizer-concept (humanizer-concept context humanizer-json)
         humanizer-concept (if revision-id
                             (assoc humanizer-concept :revision-id revision-id)
                             humanizer-concept)]
     (mdb/save-concept context humanizer-concept))))

(defn- update-humanizers-metadata
  "Update and save the humanizers metadata function which includes humanizers and community usage.
   First check if a revision exists. If so, just update either the humanizers or community usage
   metrics. Increment the revision id manually to avoid race conditions if multiple updates are
   happening at the same time.
   Returns the concept id and revision id of the saved humanizer.

   key: What we are updating :humanizers or :community-usage-metrics
   data: The data to add to the file, as a clojure map"
  [context key data]
  (if-let [humanizer-concept (find-latest-humanizer-concept context)]
    (let [humanizers (json/parse-string (:metadata humanizer-concept) true)
          humanizers (assoc humanizers key data)] ; Overwrite just the data we are saving, not the whole file
      (save-humanizers context (json/generate-string humanizers) (inc (:revision-id humanizer-concept))))
    (let [json (json/generate-string {key data})] ; No current revision exists, just write the data
      (save-humanizers context json))))

(defn get-humanizers
  "Retrieves the set of humanizer instructions from metadata-db."
  [context]
  (:humanizers (json/decode (:metadata (fetch-humanizer-concept context)) true)))

(defn get-community-usage-metrics
  "Retrieves the set of community usage metrics from metadata-db."
  [context]
  (:community-usage-metrics (json/decode (:metadata (fetch-humanizer-concept context)) true)))

(defn update-humanizers
  "Create/Update the humanizer instructions saving them as a humanizer revision in metadata db.
  Returns the concept id and revision id of the saved humanizer."
  [context humanizer-json-str]
  (let [humanizers (json/decode humanizer-json-str true)]
    (update-humanizers-metadata context :humanizers humanizers)))

(defn update-community-usage
  "Create/update the community usage metrics saving them with the humanizers in metadata db. Do not
  Do not overwrite the humanizers, just the community usage metrics. Increment the revision id
  manually to avoid race conditions if multiple updates are happening at the same time.
  Returns the concept id and revision id of the saved humanizer."
  [context community-usage-csv]
  (let [metrics (community-usage-csv->community-usage-metrics community-usage-csv)
        metrics (seq (aggregate-usage-metrics metrics))] ; Combine access-counts for entries with the same version/short-name
    (update-humanizers-metadata context :community-usage-metrics metrics)))
