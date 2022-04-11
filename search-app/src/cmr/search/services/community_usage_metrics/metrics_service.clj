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
   checks that value against both entry-title or short-name. Also checks the non-parsed value against
   short-name."
  [context product-id]
  (when (seq product-id)
    (when-let [parsed-product-id (as-> (re-find #"(^.+):|(^.+)" product-id) matches
                                   (remove nil? matches)
                                   (last matches))]
      ;; (println "querying elastic")
      (let [condition (gc/or (qm/string-condition :entry-title parsed-product-id false false)
                             (qm/string-condition :short-name parsed-product-id false false)
                             (qm/string-condition :short-name product-id false false))
            query (qm/query {:concept-type :collection
                             :condition condition
                             :page-size 1
                             :result-format :query-specified
                             :result-fields [:short-name]})
            results (qe/execute-query context query)]
        (:short-name (first (:items results)))))))

(defn- cache-or-search
  "Uses the `product` value to check the short-name-cache.  If there is a miss
  it checks the current-metrics-cache.  If the `product` is unavailable in either,
  elasticsearch is queried directly."
  [context cache current-metrics product]
  (if (.containsKey cache product)
    (.get cache product) ;; return cache hit
    (if (contains? current-metrics product)
      (do ;; current-metrics hit
        (.put cache product product)
        product) ;; return product as short-name
      (let [short-name (get-collection-by-product-id context product)] ;; query elastic
        (.put cache product short-name)
        short-name)))) ;; return queried short-name

(defn- get-short-name
  "Parse short-name from given csv-line and verify it exists in CMR.  
   Throws a service error if the product column is empty."
  [context cache csv-line product-col current-metrics]
  (let [product (read-csv-column csv-line product-col)
        _ (when-not (seq product)
            (errors/throw-service-error
             :invalid-data
             "Error parsing 'Product' CSV Data. Product may not be empty."))
        short-name (cache-or-search context cache current-metrics product)]
    (if (seq short-name)
      short-name
      (do
        (warn (format (str "While constructing community metrics humanizer, "
                           "could not find corresponding collection when searching for the term %s. "
                           "Csv line entry: %s")
                      product
                      csv-line))
        product))))

(defn- get-access-count
  "Parse access-count from given csv-line.  Throws service errors if the hosts column
   is empty or contains an invalid integer."
  [csv-line hosts-col]
  (let [access-count (read-csv-column csv-line hosts-col)]
    (if (seq access-count)
      (try
        (Integer/parseInt (str/replace access-count "," "")) ; Remove commas in large ints
        (catch java.lang.NumberFormatException e
          (errors/throw-service-error :invalid-data
                                      (format (str "Error parsing 'Hosts' CSV Data. "
                                                   "Hosts must be an integer. "
                                                   "Csv line entry: [%s]")
                                              csv-line))))
      (errors/throw-service-error :invalid-data
                                  (format (str "Error parsing 'Hosts' CSV Data. "
                                               "Hosts may not be empty. "
                                               "Csv line entry: [%s]")
                                          csv-line)))))

(defn- csv-entry->community-usage-metric
  "Convert a line in the csv file to a community usage metric. Only storing short-name (product)
   and access-count (hosts)."
  [context csv-line product-col hosts-col current-metrics cache]
  (when (seq (remove empty? csv-line)) ; Don't process empty lines
    {:short-name (get-short-name context cache csv-line product-col current-metrics)
     :access-count (get-access-count csv-line hosts-col)}))

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
  "Retrieves the current community usage metrics from metadata-db and returns
   a set of short-names."
  [context]
  (let [metrics-list (:community-usage-metrics
                      (json/decode
                       (:metadata (humanizer-service/fetch-humanizer-concept context))
                       true))]
    (into #{} (for [metric-map metrics-list]
                (:short-name metric-map)))))

(defn- community-usage-csv->community-usage-metrics
  "Validate the community usage csv and convert to a list of community usage metrics."
  [context community-usage-csv current-metrics]
  (let [{:keys [csv-lines product-col hosts-col]} (validate-and-read-csv community-usage-csv)
        short-name-cache (new java.util.HashMap)]
    (map #(csv-entry->community-usage-metric context % product-col hosts-col current-metrics short-name-cache)
         csv-lines)))

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

(defn- aggregate-usage-metrics
  "Combine access counts for entries with the same short-name."
  [metrics]
  (let [java-map (new java.util.HashMap)]
    (doseq [metric metrics]
      (let [short-name (:short-name metric)
            access-count (:access-count metric)]
        (.put java-map short-name (+ access-count (.getOrDefault java-map short-name 0)))))
    (for [item java-map]
      {:short-name (.getKey item) :access-count (.getValue item)})))

(defn update-community-usage
  "Create/update the community usage metrics saving them with the humanizers in metadata db. Do not
  Do not overwrite the humanizers, just the community usage metrics. Increment the revision id
  manually to avoid race conditions if multiple updates are happening at the same time.
  Returns the concept id and revision id of the saved humanizer."
  [context params community-usage-csv]
  (validate-update-community-usage-params params)
  (let [comprehensive (or (:comprehensive params) "true") ;; set default
        current-metrics (if (= "false" comprehensive)
                          (get-community-usage-metrics context)
                          #{})
        metrics (community-usage-csv->community-usage-metrics context community-usage-csv current-metrics)
        metrics-agg (aggregate-usage-metrics metrics)]
    (validate-metrics metric-aggs)
    (humanizer-service/update-humanizers-metadata context :community-usage-metrics metric-aggs)))
