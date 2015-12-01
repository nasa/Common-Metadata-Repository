(ns cmr.system-int-test.data2.core
  "Contains helper functions for data generation and ingest for example based testing in system
  integration tests."
  (:require [clojure.test :refer [is]]
            [clojure.java.io :as io]
            [cmr.umm.core :as umm]
            [cmr.umm-spec.core :as umm-spec]
            [cmr.umm-spec.legacy :as umm-legacy]
            [cmr.common.mime-types :as mime-types]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.url-helper :as url]
            [cmr.common.util :as util]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [cheshire.core :as json]
            [cmr.system-int-test.system :as s]
            [clojure.string :as str]))

(defn- item->native-id
  "Returns the native id of a UMM record."
  [item]
  (some #(get item %) [:granule-ur :entry-title :EntryTitle :native-id]))

(defn item->concept
  "Returns a concept map from a UMM item or tombstone. Default provider-id to PROV1 if not present."
  ([item]
   (item->concept item :echo10))
  ([item format-key]
   (let [format (mime-types/format->mime-type format-key)]
     (merge {:concept-type (umm-legacy/item->concept-type item)
             :provider-id (or (:provider-id item) "PROV1")
             :native-id (or (:native-id item) (item->native-id item))
             :metadata (when-not (:deleted item) (umm-legacy/generate-metadata (dissoc item :provider-id) format-key))
             :format format}
            (when (:concept-id item)
              {:concept-id (:concept-id item)})
            (when (:revision-id item)
              {:revision-id (:revision-id item)})))))

(defn ingest
  "Ingests the catalog item. Returns it with concept-id, revision-id, and provider-id set on it.
  Accepts a map of some optional arguments. The options are:

  * :format - The XML Metadata format to use.
  * :token - The token to use.
  * :allow-failure? - Defaults to false. If this is false an exception will be thrown when ingest fails
  * :client-id - The client-id to use
  for some reason. This is useful when you expect ingest to succeed but don't want to check the results.
  Setting it to true will skip this check. Set it true when testing ingest failure cases.
  * :validate-keywords - true or false to indicate if the validate keywords header should be sent
  to enable keyword validation. Defaults to false."
  ([provider-id item]
   (ingest provider-id item nil))
  ([provider-id item options]
   (let [format-key (get options :format :echo10)
         response (ingest/ingest-concept
                   (item->concept (assoc item :provider-id provider-id) format-key)
                   (select-keys options [:token :client-id :user-id :validate-keywords :accept-format]))
         status (:status response)]

     ;; This allows this to be used from many places where we don't expect a failure but if there is
     ;; one we'll be alerted immediately instead of through a side effect like searches failing.
     (when (and (not (:allow-failure? options)) (not= status 200))
       (throw (Exception. (str "Ingest failed when expected to succeed: "
                               (pr-str response)))))

     (if (= 200 status)
       (assoc item
              :status status
              :provider-id provider-id
              :user-id (:user-id options)
              :concept-id (:concept-id response)
              :revision-id (:revision-id response)
              :format-key format-key)
       response))))

(defn ingest-concept-with-metadata
  "Ingest the given concept with the given metadata."
  [{:keys [provider-id concept-type format-key metadata native-id]}]
  (let [concept {:concept-type concept-type
                 :provider-id provider-id
                 :native-id (or native-id "native-id")
                 :metadata metadata
                 :format (mime-types/format->mime-type format-key)}
        response (ingest/ingest-concept concept)]
    (merge (umm-legacy/parse-concept concept) response)))

(defn ingest-concept-with-metadata-file
  "Ingest the given concept with the metadata file. The metadata file has to be located on the
  classpath. Takes a metadata-filename and an ingest parameter map.

  ingest-params must contain the following keys: provider-id concept-type, and format-key. It can
  optionally contain native-id."
  [metadata-filename ingest-params]
  (let [metadata (slurp (io/resource metadata-filename))]
    (ingest-concept-with-metadata (assoc ingest-params :metadata metadata))))

(defn item->ref
  "Converts an item into the expected reference"
  [item]
  (let [{:keys [concept-id revision-id deleted]} item
        ref {:name (item->native-id item)
             :id concept-id
             :location (format "%s%s/%s" (url/location-root) (:concept-id item) revision-id)
             :revision-id revision-id}]
    (if deleted
      (-> ref
          (assoc :deleted true)
          (dissoc :location))
      ref)))

(defmulti item->metadata-result
  "Converts an item into the expected metadata result"
  (fn [echo-compatible? format-key item]
    echo-compatible?))

(defmethod item->metadata-result false
  [_ format-key item]
  (let [{:keys [concept-id revision-id collection-concept-id]} item]
    (util/remove-nil-keys
      {:concept-id concept-id
       :revision-id revision-id
       :format format-key
       :collection-concept-id collection-concept-id
       :metadata (umm-legacy/generate-metadata item format-key)})))

(defmethod item->metadata-result true
  [_ format-key item]
  (let [{:keys [concept-id revision-id collection-concept-id]} item]
    (if collection-concept-id
      (util/remove-nil-keys
        {:echo_granule_id concept-id
         :echo_dataset_id collection-concept-id
         :format format-key
         :metadata (umm-legacy/generate-metadata item format-key)})
      (util/remove-nil-keys
        {:echo_dataset_id concept-id
         :format format-key
         :metadata (umm-legacy/generate-metadata item format-key)}))))

(defn- items-match?
  "Returns true if the search result items match the expected items. The argument echo-compatible?
  when set to true converts the expected items to echo compatible format before comparing with the
  result items. format-item is a function which can be used to do additional formatting on the data
  before the comparison. Default is to not do any additional formatting."
  ([format-key items result-items & {:keys [echo-compatible? format-item]
                                     :or {echo-compatible? false
                                          format-item identity}}]
   (= (set (map #(format-item (item->metadata-result echo-compatible? format-key %)) items))
      (set (map #(format-item (dissoc % :granule-count)) result-items)))))

(defn- echo10-coverted-iso-mends-metadata-match?
  "Returns true if the concept-ids match and the result metadata indicates it is generated by xslt"
  [items search-items]
  (let [expected-ids (map :concept-id items)
        found-ids (map :concept-id search-items)]
    (and (= (set expected-ids) (set found-ids))
         (every?
           (partial re-find
                    #"<gco:CharacterString>Translated from ECHO using ECHOToISO.xsl Version: 1.31")
           (map :metadata search-items)))))

(defn- remove-metadata-ids
  "Remove random uuid strings that are added to iso mends xmls during its creation so that the xmls can be compared as strings"
  [item]
  (update-in item [:metadata]
             (fn [metadata]
               (-> metadata
                   (str/replace (re-pattern "(id=)\"[a-z0-9-]*\"") "$1\"\"")
                   (str/replace (re-pattern "(xlink:href=)\"#[a-z0-9-]*\"") "$1\"\"")))))

(defn- iso-mends-metadata-results-match?
  "Returns true if the iso-mends metadata results match the expected items"
  [format-key items search-result]
  (let [{echo10-items true non-echo10-items false} (group-by #(= :echo10 (:format-key %)) items)
        search-items (:items search-result)
        echo10-concept-ids (map :concept-id echo10-items)
        echo10-search-items (filter #(some #{(:concept-id %)} echo10-concept-ids) search-items)
        non-echo10-search-items (filter #(not (some #{(:concept-id %)} echo10-concept-ids)) search-items)]
    (and (echo10-coverted-iso-mends-metadata-match? echo10-items echo10-search-items)
         (items-match? format-key non-echo10-items non-echo10-search-items :format-item (partial remove-metadata-ids)))))

(defn metadata-results-match?
  "Returns true if the metadata results match the expected items"
  ([format-key items search-result]
   (metadata-results-match? format-key items search-result false))
  ([format-key items search-result echo-compatible?]
   (if (= :iso19115 format-key)
     (iso-mends-metadata-results-match? format-key items search-result)
     (items-match? format-key items (:items search-result) :echo-compatible? echo-compatible?))))

(defn assert-metadata-results-match
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (is (metadata-results-match? format-key items search-result)))

(defn assert-echo-compatible-metadata-results-match
  "Returns true if the metadata results match the expected items"
  [format-key items search-result]
  (is (metadata-results-match? format-key items search-result true)))

(defn echo-compatible-refs-match?
  "Returns true if the echo compatible references match the expected items"
  [items search-result]
  (let [result (= (set (map #(dissoc % :revision-id) (map item->ref items)))
                  (set (map #(dissoc % :score) (:refs search-result))))]
    (when (:status search-result)
      (println (pr-str search-result)))
    result))

(defn refs-match?
  "Returns true if the references match the expected items"
  [items search-result]
  (let [result (is (= (set (map item->ref items))
                  ;; need to remove score etc. because it won't be available in collections
                  ;; to which we are comparing
                  (set (map #(dissoc % :score :granule-count) (:refs search-result)))))]
    (when (:status search-result)
      (println (pr-str search-result)))
    result))

(defn assert-refs-match
  "Asserts that the references match the results returned. Use this in place of refs-match? to
  get better output during tests."
  [items search-result]
  (is (= (set (map item->ref items))
         ;; need to remove score etc. because it won't be available in collections
         ;; to which we are comparing
         (set (map #(dissoc % :score :granule-count) (:refs search-result))))))

(defn refs-match-order?
  "Returns true if the references match the expected items in the order specified"
  [items search-result]
  (let [result (= (map item->ref items)
                  (map #(dissoc % :score :granule-count) (:refs search-result)))]
    (when (:status search-result)
      (println (pr-str search-result)))
    result))

(defn unique-num
  "Creates a unique number and returns it"
  []
  (swap! (:unique-num-atom (s/system)) inc))

(defn unique-str
  "Creates a unique string and returns it"
  ([]
   (unique-str "string"))
  ([prefix]
   (str prefix (unique-num))))

(defn make-datetime
  "Creates a datetime from a number added onto a base datetime"
  ([n]
   (make-datetime n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n)
                        (t/hours n))]
       (if to-string?
         (f/unparse (f/formatters :date-time) date)
         date)))))

(defn make-time
  "Creates a time from a number added onto a base datetime"
  ([n]
   (make-time n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 1970 1 1 0 0 0)
                        (t/minutes n)
                        (t/seconds n))]
       (if to-string?
         (f/unparse (f/formatters :hour-minute-second) date)
         date)))))

(defn make-date
  "Creates a date from a number added onto a base datetime"
  ([n]
   (make-date n true))
  ([n to-string?]
   (when n
     (let [date (t/plus (t/date-time 2012 1 1 0 0 0)
                        (t/days n))]
       (if to-string?
         (f/unparse (f/formatters :date) date)
         date)))))
