(ns cmr.search.services.community-usage-metrics.metrics-service
  "Provides functions for storing and retrieving community usage metrics. Community usage metrics
   are saved in MetadataDB as part of the humanizers JSON."
  (:require
   [cheshire.core :as json]
   [clojure.data.csv :as csv]
   [clojure.string :as str]
   [cmr.common-app.services.search.parameter-validation :as cpv]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common.log :as log :refer (debug info warn error)]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :as util]
   [cmr.search.models.query :as advanced-query]
   [cmr.search.services.community-usage-metrics.metrics-json-schema-validation :as metrics-json]
   [cmr.search.services.humanizers.humanizer-service :as humanizer-service]))

(defn- get-community-usage-columns
  "The community usage csv has many columns. Get the indices of the columns we want to store.

   Returns:
   product-col - the index of the Product in each CSV line
   version-col - the index of the Version in each CSV line
   hosts-col - the index of the Hosts in each CSV line"
  [csv-header]
  (let [csv-header (mapv str/trim csv-header)]
   {:product-col (.indexOf csv-header "Product")
    :version-col (.indexOf csv-header "Version")
    :hosts-col (.indexOf csv-header "Hosts")}))

(defn- read-csv-column
  "Read a column from the csv, if the column is exists. Otherwise, return nil."
  [csv-line col]
  (when (>= col 0)
    (nth csv-line col)))

(defn- get-collection-by-product-id
  "Query elastic for a collection with a given product-id, parses out the value before the last : and
   checks that value against entry-title, short-name, and Persistent ID attribute. Also checks the non-parsed value against
   short-name."
  [context product-id]
  (when (seq product-id)
    (when-let [parsed-product-id (as-> (re-find #"(^.+):|(^.+)" product-id) matches
                                       (remove nil? matches)
                                       (last matches))]
      (let [condition (gc/or (qm/string-condition :entry-title parsed-product-id false false)
                             (qm/string-condition :short-name parsed-product-id false false)
                             (qm/string-condition :short-name product-id false false)
                             ;; Handle PODAAC case
                             (advanced-query/map->AttributeValueCondition
                              {:name "Persistent ID"
                               :type :string
                               :value product-id}))
            query (qm/query {:concept-type :collection
                             :condition condition
                             :page-size 1
                             :result-format :query-specified
                             :result-fields [:short-name]})
            results (qe/execute-query context query)]
        (:short-name (first (:items results)))))))

(defn- comprehensive-collection-short-name-search
  "This function will check for a collection with the given product value against short-name and entry-title
   and return its short-name.  Currently there are providers who are supplying non-short-name values
   in short-name, which breaks usage metrics.  This is a short term solution to this problem,
   the long term solution is correcting the EMS data which may take a long time to happen."
  [context short-name]
  (get-collection-by-product-id context short-name))

(defn- efficient-collection-short-name-search
  "This function will check the current community-usage-metrics humanizer to see if this shortname exists,
   if it does, then it will return that short-name, otherwise it will try looking up the short-name via
   product-id."
  [context short-name current-metrics]
  (if (get current-metrics short-name)
    short-name
    (comprehensive-collection-short-name-search context short-name)))

(defn- csv-entry->community-usage-metric
  "Convert a line in the csv file to a community usage metric. Only storing short-name (product),
   version (can be 'N/A' or a version number), and access-count (hosts)"
  [context csv-line product-col version-col hosts-col comprehensive current-metrics]
  (when (seq (remove empty? csv-line)) ; Don't process empty lines
    (let [short-name (read-csv-column csv-line product-col)]
      (when-not (seq short-name)
        (errors/throw-service-error :invalid-data
          "Error parsing 'Product' CSV Data. Product may not be empty."))
      (let [version (read-csv-column csv-line version-col)
            searched-short-name (if (= "true" comprehensive)
                                  (comprehensive-collection-short-name-search context short-name)
                                  (efficient-collection-short-name-search context short-name current-metrics))]
        {:short-name (if (seq searched-short-name)
                       searched-short-name
                       (do
                         (warn (format (str "While constructing community metrics humanizer, "
                                            "could not find corresponding collection when searching for the term %s. "
                                            "Csv line entry: %s")
                                       short-name
                                       csv-line))
                         short-name))
         :version version
         :access-count (let [access-count (read-csv-column csv-line hosts-col)]
                         (if (seq access-count)
                           (try
                             (Integer/parseInt (str/replace access-count "," "")) ; Remove commas in large ints
                             (catch java.lang.NumberFormatException e
                               (errors/throw-service-error :invalid-data
                                 (format (str "Error parsing 'Hosts' CSV Data for collection [%s], version "
                                              "[%s]. Hosts must be an integer.")
                                   short-name version))))
                          (errors/throw-service-error :invalid-data
                            (format (str "Error parsing 'Hosts' CSV Data for collection [%s], version "
                                          "[%s]. Hosts may not be empty.")
                              short-name version))))}))))

(defn- validate-and-read-csv
  "Validate the ingested community usage metrics csv and if valid, return the data lines read
  from the CSV (everything except the header) and column indices of data we want to store. If there
  is invalid data, throw an error.

  Perform the following validations:
   * CSV is neither nil nor empty
   * A Product column exists
   * A Hosts column exists"
  [community-usage-csv]
  (if community-usage-csv
    (if-let [csv-lines (seq (csv/read-csv community-usage-csv))]
      (let [csv-columns (get-community-usage-columns (first csv-lines))]
        (when (< (:product-col csv-columns) 0)
          (errors/throw-service-error :invalid-data "A 'Product' column is required in community usage CSV data"))
        (when (< (:hosts-col csv-columns) 0)
          (errors/throw-service-error :invalid-data "A 'Hosts' column is required in community usage CSV data"))
        (merge {:csv-lines (rest csv-lines)} csv-columns))
      (errors/throw-service-error :invalid-data "You posted empty content"))
    (errors/throw-service-error :invalid-data "You posted empty content")))

(defn get-community-usage-metrics
  "Retrieves the set of community usage metrics from metadata-db."
  [context]
  (:community-usage-metrics (json/decode (:metadata (humanizer-service/fetch-humanizer-concept context)) true)))

(defn- community-usage-csv->community-usage-metrics
  "Validate the community usage csv and convert to a list of community usage metrics to save"
  [context community-usage-csv comprehensive]
  (let [{:keys [csv-lines product-col version-col hosts-col]} (validate-and-read-csv community-usage-csv)
        current-metrics (when (= "false" comprehensive)
                          (get-community-usage-metrics context))]
    (map #(csv-entry->community-usage-metric context % product-col version-col hosts-col comprehensive current-metrics)
         csv-lines)))

(defn- aggregate-usage-metrics
  "Combine access counts for entries with the same short-name and version number."
  [metrics]
  ;; name-version-groups is map of [short-name, version] [entries that match short-name/version]
  (let [name-version-groups (group-by (juxt :short-name :version) metrics)] ; Group by short-name and version
    ;; The first entry in each list has the short-name and version we want so just add up the access-counts
    ;; in the rest and add that to the first entry to make the access-counts right
    (map #(util/remove-nil-keys (assoc (first %) :access-count (reduce + (map :access-count %))))
         (vals name-version-groups))))

(defn- validate-metrics
  "Validate metrics against the JSON schema validation"
  [metrics]
  (let [json (json/generate-string metrics)]
    (metrics-json/validate-metrics-json json)))

(defn- validate-update-community-usage-params
  "Currently only validates the parameter comprehensive as a boolean."
  [params]
  (cpv/validate-parameters
   nil
   params
   [(partial cpv/validate-boolean-param :comprehensive)]))

(defn update-community-usage
  "Create/update the community usage metrics saving them with the humanizers in metadata db. Do not
  Do not overwrite the humanizers, just the community usage metrics. Increment the revision id
  manually to avoid race conditions if multiple updates are happening at the same time.
  Returns the concept id and revision id of the saved humanizer."
  [context params community-usage-csv]
  (validate-update-community-usage-params params)
  (let [metrics (community-usage-csv->community-usage-metrics context community-usage-csv (:comprehensive params))
        metrics (seq (aggregate-usage-metrics metrics))] ; Combine access-counts for entries with the same version/short-name
    (validate-metrics metrics)
    (humanizer-service/update-humanizers-metadata context :community-usage-metrics metrics)))
