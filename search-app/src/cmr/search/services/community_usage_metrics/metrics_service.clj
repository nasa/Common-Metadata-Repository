(ns cmr.search.services.community-usage-metrics.metrics-service
  "Provides functions for storing and retrieving community usage metrics. Community usage metrics
   are saved in MetadataDB as part of the humanizers JSON."
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.services.community-usage-metrics.metrics-json-schema-validation :as metrics-json]
   [cmr.search.services.humanizers.humanizer-service :as humanizer-service]))

(defn- get-community-usage-columns
  "The community usage csv has many columns. Get the indices of the columns we want to store.

   Returns:
   product-col - the index of the Product in each CSV line
   version-col - the index of the Version in each CSV line
   hosts-col - the index of the Hosts in each CSV line"
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
  (let [short-name (read-csv-column csv-line product-col)
        version (read-csv-column csv-line version-col)]
    {:short-name short-name
     :version version
     :access-count (when-let [access-count (read-csv-column csv-line hosts-col)]
                     (try
                       (Integer/parseInt access-count)
                       (catch java.lang.NumberFormatException e
                         (errors/throw-service-error :invalid-data
                                                     (format "Error parsing 'Hosts' CSV Data for collection [%s], version [%s]. Hosts must be an integer."
                                                             short-name version)))))}))

(defn- community-usage-csv->community-usage-metrics
  "Convert the community usage csv to a list of community usage metrics to save"
  [community-usage-csv]
  (when-let [csv-lines (seq (csv/read-csv community-usage-csv))]
    (let [{:keys [product-col version-col hosts-col]} (get-community-usage-columns (first csv-lines))]
      (map #(csv-entry->community-usage-metric % product-col version-col hosts-col) (rest csv-lines)))))

(defn- aggregate-usage-metrics
  "Combine access counts for entries with the same short-name and version number."
  [metrics]
  (let [name-version-groups (group-by (juxt :short-name :version) metrics)] ; Group by short-name and version
    ;; name-version-groups is map of [short-name, version] [entries that match short-name/version]
    ;; The first entry in each list has the short-name and version we want so just add up the access-counts
    ;; in the rest and add that to the first entry to make the access-counts right
    (map #(util/remove-nil-keys (assoc (first %) :access-count (reduce + (map :access-count %))))
         (vals name-version-groups))))

(defn- validate-metrics
  "Validate metrics against the JSON schema validation"
  [metrics]
  (let [json (json/generate-string metrics)]
    (metrics-json/validate-metrics-json json)))

(defn get-community-usage-metrics
  "Retrieves the set of community usage metrics from metadata-db."
  [context]
  (:community-usage-metrics (json/decode (:metadata (humanizer-service/fetch-humanizer-concept context)) true)))

(defn update-community-usage
  "Create/update the community usage metrics saving them with the humanizers in metadata db. Do not
  Do not overwrite the humanizers, just the community usage metrics. Increment the revision id
  manually to avoid race conditions if multiple updates are happening at the same time.
  Returns the concept id and revision id of the saved humanizer."
  [context community-usage-csv]
  (let [metrics (community-usage-csv->community-usage-metrics community-usage-csv)
        metrics (seq (aggregate-usage-metrics metrics))] ; Combine access-counts for entries with the same version/short-name
    (validate-metrics metrics)
    (humanizer-service/update-humanizers-metadata context :community-usage-metrics metrics)))
