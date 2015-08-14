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
  (:require [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clj-http.client :as client]
            [camel-snake-kebab.core :as csk]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.util :as util]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn- find-invalid-entries
  "Checks the entries for any duplicate short names. Short names should be unique otherwise we
  do not know how to correctly map from short name to the full hierarchy.

  Takes a list of the keywords represented as a map with each subfield name being a key.
  Returns a sequence of invalid entries."
  [keyword-entries]
  (->> keyword-entries
       (group-by :short-name)
       ;; Keep all the ones that have duplicate short names
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
  "Number of lines which contain header information (not the actual keyword values)."
  2)

(defn- parse-entries-from-csv
  "Parses the CSV returned by the GCMD KMS. It is expected that the CSV will be returned in a
  specific format with the first line providing metadata information, the second line providing
  a breakdown of the subfields for the keyword scheme, and from the third line on are the actual
  values.

  Returns a map with each short-name as a key and the full hierarchy map for each keyword as the
  value."
  [keyword-scheme csv-content]
  (let [all-lines (csv/read-csv csv-content)
        ;; Line 2 contains the names of the subfield names
        subfield-names (map csk/->kebab-case-keyword (second all-lines))
        keyword-entries (->> all-lines
                             (drop NUM_HEADER_LINES)
                             ;; Create a map for each row using the subfield-names as keys
                             (map #(zipmap subfield-names %))
                             (map remove-blank-keys)
                             ;; We only want keyword entries with a short-name (leaf entries)
                             (filter :short-name))
        invalid-entries (find-invalid-entries keyword-entries)]

    ;; Print out warnings for any duplicate keywords so that we can create a Splunk alert.
    (doseq [entry invalid-entries]
      (warn (format "Found duplicate keywords for %s short-name [%s]: %s" (name keyword-scheme)
                    (:short-name entry) entry)))

    ;; Create a map with the short-names as keys to the full hierarchy for that short-name
    (into {}
          (for [entry keyword-entries
                :let [{:keys [short-name]} entry]]
            [short-name entry]))))

(defn- get-by-keyword-scheme
  "Makes a get request to the GCMD KMS. Returns the controlled vocabulary map for the given
  keyword scheme."
  [context keyword-scheme]
  (let [conn (config/context->app-connection context :kms)
        url (format "%s/%s/%s.csv"
                    (conn/root-url conn)
                    (name keyword-scheme)
                    (name keyword-scheme))
        params {:connection-manager (conn/conn-mgr conn)
                :throw-exceptions true}
        start (System/currentTimeMillis)
        response (client/get url params)]
    (debug
      (format
        "Completed KMS Request to %s in [%d] ms" url (- (System/currentTimeMillis) start)))
    (:body response)))

;; Public API

(defn get-keywords-for-keyword-scheme
  "Returns the full list of keywords from the GCMD Keyword Management System (KMS) for the given
  keyword scheme. Supported keyword schemes include providers, platforms, and instruments.

  Returns a map with each short-name as a key and the full hierarchy map for each keyword as the
  value.

  Example response:
  {\"ETALON-2\"
  {:uuid \"c9c07cf0-49eb-4c7f-aeff-2e95caae9500\", :short-name \"ETALON-2\",
  :series-entity \"ETALON\", :category \"Earth Observation Satellites\"} ..."
  [context keyword-scheme]
  (let [keywords
        (parse-entries-from-csv keyword-scheme (get-by-keyword-scheme context keyword-scheme))]
    (debug (format "Found %s keywords for %s" (count (keys keywords)) (name keyword-scheme)))
    keywords))

(comment
  (take 3 (get-keywords-for-keyword-scheme {:system (cmr.indexer.system/create-system)} :providers))
  (take 3 (get-keywords-for-keyword-scheme {:system (cmr.indexer.system/create-system)} :instruments))
  (take 3 (get-keywords-for-keyword-scheme {:system (cmr.indexer.system/create-system)} :platforms))
  )
