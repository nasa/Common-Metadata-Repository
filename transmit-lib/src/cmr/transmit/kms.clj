(ns cmr.transmit.kms
  "This namespace handles retrieval of controlled vocabulary from the GCMD Keyword Management
  System (KMS)."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.util :as util]
            [cmr.common.log :as log :refer (debug info warn error)]))

(def leaf-node-field-name
  "A map of the concept-scheme to the field name within that concept-scheme that identifies an
  entry as a leaf node."
  {:providers :Short_Name})

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

(defn- parse-entries-from-csv
  "Parses the CSV returned by the GCMD KMS. It is expected that the CSV will be returned in a
  specific format with the first line providing metadata information, the second line providing
  a breakdown of the subfields within the hierarchy, and from the third line on are the actual
  values.

  Returns an array of maps containing the subfield names as keys for each of the values."
  [concept-scheme csv-content]
  (let [all-lines (str/split-lines csv-content)
        ;; Line 2 contains the names of the subfield names
        subfield-names (map keyword (parse-single-csv-line (second all-lines) ","))
        keyword-values (map util/remove-blank-keys
                            (map #(zipmap subfield-names (parse-single-csv-line % "\",\""))
                            ;; Lines 3 to the end are the values
                            (rest (rest all-lines))))]

    ;; Filter out any values that are not leaf nodes
    (filter (leaf-node-field-name concept-scheme) keyword-values)))

(comment
  (take 10 (get-provider-hierarchy {:system (cmr.search.system/create-system)}))
  (count (set (map :Short_Name (get-provider-hierarchy {:system (cmr.search.system/create-system)}))))
  (count (map :Short_Name (get-provider-hierarchy {:system (cmr.search.system/create-system)})))
  ;; Look for any duplicate providers
  (for [v (vals (group-by keyword (map :Short_Name (get-provider-hierarchy
                                                     {:system (cmr.search.system/create-system)}))))
        :when (> (count v) 1)]
    v))

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

(defn get-provider-hierarchy
  "Returns the provider hierarchy"
  [context]
  ; (parse-entries-from-csv :providers (:body (get-by-concept-scheme context :providers))))
  (parse-entries-from-csv :providers (slurp (clojure.java.io/resource "provider_kms.csv"))))

