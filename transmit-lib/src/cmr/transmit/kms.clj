(ns cmr.transmit.kms
  "This namespace handles retrieval of controlled keywords from the GCMD Keyword Management
  System (KMS). There are several different keyword schemes within KMS. They include providers,
  platforms, instruments, science keywords, and locations. This namespace currently supports
  providers, platforms, and instruments.

  For each of the supported keyword schemes we expect the short name to uniquely identify a row
  in the KMS. However we have found that the actual KMS does contain duplicates. Until the GCMD
  enforces uniqueness we will track any duplicate short names so that we can make GCMD aware and
  they fix the entries.

  We utilize the clojure.data.csv libary to handle parsing the CSV files. Example KMS keyword files
  can be found in dev-system/resources/kms_examples."
  (:require
    [camel-snake-kebab.core :as csk]
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clojure.data.csv :as csv]
    [clojure.data.xml :as xml]
    [clojure.java.io :as jio]
    [clojure.set :as set]
    [clojure.string :as str]
    [cmr.common.log :as log :refer (debug info warn error)]
    [cmr.common.util :as util]
    [cmr.common.xml.simple-xpath :refer [select]]
    [cmr.transmit.config :as config]
    [cmr.transmit.connection :as conn]))

(def keyword-scheme->leaf-field-name
  "Maps each keyword scheme to the subfield which identifies the keyword as a leaf node."
  {:providers :short-name
   :platforms :short-name
   :instruments :short-name
   :projects :short-name
   :temporal-keywords :temporal-resolution-range
   :spatial-keywords :uuid
   :spatial-keywords-old :uuid
   :science-keywords :uuid
   :measurement-name :object
   :concepts :short-name
   :iso-topic-categories :uuid
   :related-urls :uuid
   :granule-data-format :uuid
   :mime-type :uuid})

(comment
 "The following map contains code used for trasitioning CMR from SIT->UAT->PROD
  and keeping in step with KMS changes. These three envirments all talk to
  production KMS due to AWS->EBNET network limitations. Because of of this we
  need to be able to publish data for all three envirments with one host. while
  not ideal, it was decided to use the different 'version' capabilities in KMS
  to isolate production from the other two envirments. Specificly: rucontenttype
  is a three level tree in SIT, but not PROD. This process is only needed durring
  the transition and should be deleted once in production.

  To override the scheme KMS settings, set the config kms-scheme-override-json to:
  {\"platforms\":\"static\",
   \"mime-type\":\"mimetype?format=csv&version=special\"}"
 )

(defn- scheme-overrides
  "CMR will Allow for any KMS resource URL to be overriden by a config
   variable (AWS parameter). The config is assumed to contain a string with JSON.
   Return value is the KMS schema override map as configured in AWS as
   CMR_KMS_SCHEMA_OVERRIDE_JSON."
  []
  (let [overrides (config/kms-scheme-override-json)]
    (info (format "Requested KMS schema overrides: %s." overrides))
    (try
      (json/parse-string overrides true)
      (catch com.fasterxml.jackson.core.JsonParseException e
        (error "Failed to parse Scheme Override JSON while loading KMS resource [%s]: %s",
               (config/kms-scheme-override-json)
               (.getMessage e))))))

;; These are the default locations in KMS for all supported schemes
(def keyword-scheme->gcmd-resource-name
  "Maps each keyword scheme to the GCMD resource name"
  {:providers "providers?format=csv"
   :platforms "platforms?format=csv"
   :instruments "instruments?format=csv"
   :projects "projects?format=csv"
   :temporal-keywords "temporalresolutionrange?format=csv"
   :spatial-keywords "locations?format=csv"
   :spatial-keywords-old "locations?format=csv"
   :science-keywords "sciencekeywords?format=csv"
   :measurement-name "measurementname?format=csv"
   :concepts "idnnode?format=csv"
   :iso-topic-categories "isotopiccategory?format=csv"
   :related-urls "rucontenttype?format=csv"
   :granule-data-format "dataformat?format=csv"
   :mime-type "mimetype?format=csv"})

(defn- keyword-scheme->kms-resource
  "This is the primary way to get KMS URL resource locations. This function will
   first try to use the default values, but if not production the alt values will
   be applied. Finally the ENV scheme overrides are applied. Any value of
    `static` will cause CMR to load a local file under ingest."
  [keyword-scheme]
  (keyword-scheme (merge keyword-scheme->gcmd-resource-name (scheme-overrides))))

(def keyword-scheme->field-names
  "Maps each keyword scheme to its subfield names. These values are also used to
  decide which concepts will be cached"
  {:providers [:level-0 :level-1 :level-2 :level-3 :short-name :long-name :url :uuid]
   :platforms [:basis :category :sub-category :short-name :long-name :uuid]
   :instruments [:category :class :type :subtype :short-name :long-name :uuid]
   :projects [:bucket :short-name :long-name :uuid]
   :temporal-keywords [:temporal-resolution-range :uuid]
   :spatial-keywords [:category :type :subregion-1 :subregion-2 :subregion-3 :subregion-4 :uuid]
   :spatial-keywords-old [:category :type :subregion-1 :subregion-2 :subregion-3 :uuid]
   :science-keywords [:category :topic :term :variable-level-1 :variable-level-2 :variable-level-3
                      :detailed-variable :uuid]
   :measurement-name [:context-medium :object :quantity :uuid]
   :concepts [:short-name :long-name :uuid]
   :iso-topic-categories [:iso-topic-category :uuid]
   :related-urls [:url-content-type :type :subtype :uuid]
   :granule-data-format [:short-name :long-name :uuid]
   :mime-type [:mime-type :uuid]})

(def keyword-scheme->expected-field-names
  "Maps each keyword scheme to the expected field names to be returned by KMS. We changed
  the names of some fields to provide a better name on our API."
  (merge keyword-scheme->field-names
         {:providers [:bucket-level-0 :bucket-level-1 :bucket-level-2 :bucket-level-3 :short-name
                      :long-name :data-center-url :uuid]
          :spatial-keywords [:location-category :location-type :location-subregion-1
                             :location-subregion-2 :location-subregion-3 :location-subregion-4 :uuid]
          :spatial-keywords-old [:location-category :location-type :location-subregion-1
                             :location-subregion-2 :location-subregion-3 :uuid]}))

(def keyword-scheme->required-field
  "Maps each keyword scheme to a field that must be present for a keyword to be valid."
  (merge keyword-scheme->leaf-field-name
         {:science-keywords :term
          :spatial-keywords :category
          :spatial-keywords-old :category
          :granule-data-format :uuid}))

(def cmr-to-gcmd-keyword-scheme-aliases
  "Map of all keyword schemes which are referred to with a different name within CMR and GCMD."
  {:archive-centers :providers
   :data-centers :providers
   :location-keywords :spatial-keywords})

(defn translate-keyword-scheme-to-gcmd
  "Translates a keyword scheme into a known keyword scheme for GCMD."
  [keyword-scheme]
  (get cmr-to-gcmd-keyword-scheme-aliases keyword-scheme keyword-scheme))

(defn translate-keyword-scheme-to-cmr
  "Translates a keyword scheme into a known keyword scheme for CMR."
  [keyword-scheme]
  (get (set/map-invert cmr-to-gcmd-keyword-scheme-aliases) keyword-scheme keyword-scheme))

(defn- find-invalid-entries
  "Checks the entries for any duplicate leaf field values. The leaf field should be unique
  otherwise we do not know how to correctly map from the provided leaf value to the full hierarchy.

  Takes a list of the keywords represented as a map with each subfield name being a key.
  Returns a sequence of invalid entries."
  [keyword-entries leaf-field-name]
  (->> keyword-entries
       (group-by leaf-field-name)
       ;; Keep all the ones that have duplicate leaf field names
       (util/remove-map-keys #(= (count %) 1))
       ;; Get all the entries with duplicates as a single sequence
       vals
       flatten))

(defn- remove-blank-keys
  "Remove any keys from a map which have nil or empty string values."
  [m]
  (util/remove-map-keys
    (fn [v] (or (nil? v) (and (string? v) (str/blank? v))))
    m))

(def NUM_HEADER_LINES
  "Number of lines which contain header information in csv files (not the actual keyword values)."
  2)

(defn- get-spatial-scheme-to-use
  "Figures out if the KMS is returning the subregion-3 or subregion-4 data for spatial-keywords."
  [subfield-names]
  (if (= (:spatial-keywords keyword-scheme->expected-field-names) subfield-names)
    :spatial-keywords
    :spatial-keywords-old))

(defn- subfield-names-valid?
  "Validates that the provided subfield names match the expected subfield names for the given
  keyword scheme. Returns true if they match, false otherwise."
  [keyword-scheme subfield-names]
  (let [expected-subfield-names (keyword-scheme keyword-scheme->expected-field-names)
        names-match-result (= expected-subfield-names subfield-names)]
        ;; allow spatial-keywords that contain either 3 or 4 subregions.
    (when-not names-match-result
      (error (format "Expected subfield names for %s to be %s, but were %s."
                     (name keyword-scheme)
                     (pr-str expected-subfield-names)
                     (pr-str subfield-names))))
    names-match-result))

(defn- remove-not-yet-supported-fields
  "Removes the spatial-keywords subregion-4 keyword if it exists. The CMR is not ready to use
   subregion-4, but KMS has already implemented it. This allows the CMR to use either the subregion-3
   or subregion-4 KMS output."
  [keyword-scheme keyword-entries]
  (def keyword-entries keyword-entries)
  (if (= keyword-scheme :spatial-keywords)
    (map #(dissoc % :subregion-4) keyword-entries)
    keyword-entries))

(defn- parse-entries-from-csv
  "Parses the CSV returned by the GCMD KMS. It is expected that the CSV will be returned in a
  specific format with the first line providing metadata information, the second line providing
  a breakdown of the subfields for the keyword scheme, and from the third line on are the actual
  values.

  keyword-scheme shoud be something like :platforms
  csv-content is the raw text of the CSV file to parse
  Returns a sequence of full hierarchy maps or nil if subfield names do not match expected."
  [keyword-scheme csv-content]
  (let [all-lines (csv/read-csv csv-content)
        ;; Line 2 contains the subfield names
        kms-subfield-names (map csk/->kebab-case-keyword (second all-lines))
        keyword-scheme (if (= keyword-scheme :spatial-keywords)
                         (get-spatial-scheme-to-use kms-subfield-names)
                         keyword-scheme)]
    (when (subfield-names-valid? keyword-scheme kms-subfield-names)
      (let [keyword-entries (->> all-lines
                                 (drop NUM_HEADER_LINES)
                                 ;; Create a map for each row using the subfield-names as keys
                                 (map #(zipmap (keyword-scheme keyword-scheme->field-names) %))
                                 (map remove-blank-keys)
                                 ;; Only keep entries which map to full valid keywords
                                 (filter (keyword-scheme->required-field keyword-scheme))
                                 (remove-not-yet-supported-fields keyword-scheme))
            leaf-field-name (keyword-scheme keyword-scheme->leaf-field-name)
            invalid-entries (find-invalid-entries keyword-entries leaf-field-name)]
        ;; Print out warnings for any duplicate keywords so that we can create a Splunk alert.
        (doseq [entry invalid-entries]
          (warn (format "Found duplicate keywords for %s short-name [%s]: %s" (name keyword-scheme)
                        (:short-name entry) entry)))
        keyword-entries))))

(defn- get-by-keyword-scheme
  "Makes a get request to the GCMD KMS. Returns the controlled vocabulary map for the given
  keyword scheme."
  [context keyword-scheme]

  ;; From time to time KMS will not be ready to host a set of keywords as the
  ;; population of that system happens on it's team's own schedule. In the case
  ;; where a standard list is ready before the KMS sever is, load that list from
  ;; a file internal to CMR and not the service. GCMD resource names with the
  ;; value `frozen` will tell CMR that a local file is to be used. This
  ;; process is driven by the config value `kms_scheme_overrides`
  ;; which contains a string of JSON such as:
  ;;     "{\"platforms\":\"static\"}"
  (let [gcmd-resource-name (keyword-scheme->kms-resource keyword-scheme)]
    (info (format "Loading KMS resource [%s] for [%s]..." gcmd-resource-name keyword-scheme))
    (if (= "static" (str/lower-case gcmd-resource-name))
      ;; load the static file from the resource directory under indexer
      (let [gcmd-resource-path (str (format "static_kms_keywords/%s.csv" (name keyword-scheme)))
            data (slurp (jio/resource gcmd-resource-path))
            data-as-rows (str/split-lines (or data ""))
            version-info (first (str/split (first data-as-rows) #","))
            header (str/split-lines (second data-as-rows))]
        (info (format "Loading static KMS resource from %s for %s. %s. Found keys [%s]."
                      gcmd-resource-path
                      gcmd-resource-name
                      version-info
                      header))
        data)
      (let [conn (config/context->app-connection context :kms)
            url (format "%s/%s" (conn/root-url conn) gcmd-resource-name)
            params (merge
                    (config/conn-params conn)
                    {:headers {:accept-charset "utf-8"}
                     :throw-exceptions true})
            start (System/currentTimeMillis)
            response (client/get url params)]
        (info
         (format
          "Completed KMS Request to %s in [%d] ms" url (- (System/currentTimeMillis) start)))
        (:body response)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public API

(defn get-keywords-for-keyword-scheme
  "Returns the full list of keywords from the GCMD Keyword Management System (KMS) for the given
  keyword scheme. Supported keyword schemes include providers, platforms, and instruments.

  Returns a map with each short-name as a key and the full hierarchy map for each keyword as the
  value.

  Example response:
  {\"ETALON-2\"
  {:uuid \"c9c07cf0-49eb-4c7f-aeff-2e95caae9500\", :long-name \"Etalan two\",
   :short-name \"ETALON-2\", :sub-category \"ETALON\",
   :category \"Earth Observation Satellites\" :basis \"Space-based Platforms\"} ..."
  [context keyword-scheme]
  {:pre (some? (keyword-scheme keyword-scheme->field-names))}
  (when-not (= keyword-scheme :spatial-keywords-old)
    (let [keywords
           (parse-entries-from-csv keyword-scheme (get-by-keyword-scheme context keyword-scheme))]
      (info (format "Found %s keywords for %s" (count (keys keywords)) (name keyword-scheme)))
      keywords)))

(comment
  (def get-keywords-from-system
    (partial get-keywords-for-keyword-scheme {:system (cmr.indexer.system/create-system)}))
  (get-keywords-from-system :measurement-name)
  (config/set-kms-scheme-override-json! "{\"platforms\": \"static\"}")
  (first (get-keywords-from-system :platforms))
  (parse-entries-from-csv :platforms (slurp (jio/resource "static_kms_keywords/platforms.csv")))
  )
