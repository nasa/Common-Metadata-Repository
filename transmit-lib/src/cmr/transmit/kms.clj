(ns cmr.transmit.kms
  "This namespace handles retrieval of controlled vocabulary from the GCMD Keyword Management
  System (KMS)."
  (:require [clojure.string :as str]
            [clj-http.client :as client]
            [camel-snake-kebab.core :as csk]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.util :as util]
            [cmr.common.log :as log :refer (debug info warn error)]))

(defn- parse-single-csv-line
  "Parses a single CSV line into an array of values. An example line:

  \"U.S. STATE/REGIONAL/LOCAL AGENCIES\",\"WYOMING\",\"\",\"\",\"WY/TC/DEM\",\"Department of
  Emergency Management,Teton County, Wyoming\",\"http://www.tetonwyo.org/em/\",
  \"d51f97c7-387a-4794-b445-bb1daa486cde\""
  [csv-line field-separator]
  (-> csv-line
      (str/replace (re-pattern "^\"") "")
      (str/replace (re-pattern "\"$") "")
      (str/split (re-pattern field-separator))))

(defn- log-warning-for-invalid-entries
  "Checks the entries for any duplicate short names. Short names should be unique otherwise we
  do not know how to correctly map from short name to the full hierarchy. We log a warning, and
  choose one of the hierarchies at random."
  [keyword-entries]
  (let [duplicates (for [v (vals (group-by keyword (map :short-name keyword-entries)))
                         :when (> (count v) 1)]
                     (first v))]
    (doseq [short-name duplicates :when short-name]
      (doseq [entry keyword-entries :when (= short-name (:short-name entry))]
        (warn (format "Found duplicate controlled vocabulary for short-name [%s]: %s"
                      short-name
                      entry))))))

(defn- parse-entries-from-csv
  "Parses the CSV returned by the GCMD KMS. It is expected that the CSV will be returned in a
  specific format with the first line providing metadata information, the second line providing
  a breakdown of the subfields within the hierarchy, and from the third line on are the actual
  values.

  Returns a map with each short-name as a key and the full hierarchy map for each keyword as the
  value. andmaps containing the subfield names as keys for each of the values."
  [concept-scheme csv-content]
  (let [all-lines (str/split-lines csv-content)
        ;; Line 2 contains the names of the subfield names
        subfield-names (map csk/->kebab-case-keyword (parse-single-csv-line (second all-lines) ","))
        keyword-entries (map util/remove-blank-keys
                             (map #(zipmap subfield-names (parse-single-csv-line % "\",\""))
                                  ;; Lines 3 to the end are the values
                                  (rest (rest all-lines))))]

    (log-warning-for-invalid-entries keyword-entries)

    ;; Create a map with the short-names as keys to the full hierarchy for that short-name
    (into {}
          (for [entry keyword-entries
                :let [{:keys [short-name]} entry]
                :when short-name]
            [short-name entry]))))

(defn- get-by-concept-scheme
  "Makes a get request to the GCMD KMS. Returns the controlled vocabulary map for the given
  concept-scheme"
  ([context concept-scheme]
   (get-by-concept-scheme context concept-scheme {}))
  ([context concept-scheme options]
   (let [conn (config/context->app-connection context :kms)
         url (format "%s/%s/%s.csv"
                     (conn/root-url conn)
                     (name concept-scheme)
                     (name concept-scheme))
         params (merge {:throw-exceptions false
                        :connection-manager (conn/conn-mgr conn)}
                       options)
         start (System/currentTimeMillis)
         response (client/get url params)]
     (debug
       (format
         "Completed KMS Request to %s in [%d] ms" url (- (System/currentTimeMillis) start)))
     response)))

;; Public API

(defn get-keywords-for-concept-scheme
  "Returns the full list of keywords from the GCMD Keyword Management System (KMS) for the given
  concept scheme. Supported concept schemes include providers, platforms, and instruments."
  [context concept-scheme]
  (let [keywords (parse-entries-from-csv concept-scheme
                                         (:body (get-by-concept-scheme context concept-scheme)))]
    (debug (format "Found %s keywords for %s" (count (keys keywords)) (name concept-scheme)))
    keywords))
  ; (parse-entries-from-csv :providers (slurp (clojure.java.io/resource "provider_kms.csv"))))

(comment
  (take 3 (get-keywords-for-concept-scheme {:system (cmr.search.system/create-system)} :providers))
  )
