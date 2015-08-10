(ns cmr.transmit.kms
  "This namespace handles retrieval of controlled vocabulary from the GCMD Keyword Management
  System (KMS)."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [cmr.transmit.config :as config]
            [cmr.transmit.connection :as conn]
            [cmr.common.log :as log :refer (debug info warn error)]))

(def leaf-node-field
  "A map of the concept-scheme to the field within that concept-scheme that identifies an entry
  as a leaf node."
  {:providers :Short_Name})

(comment
  (zipmap [:1 :2 :3] (str/split "\"abc\",\"def\"" #","))
  (str/split "\"abc\",\"def\"" #",")
  (map keyword (str/split (str/replace "\"B1\",\"B2\",\"short-name\""
                                       "\""
                                       "")
                          #",")))

(defn- parse-entries-from-csv
  "Parses the CSV returned by the GCMD KMS. It is expected that the CSV will be returned in a
  specific format with the first line providing metadata information, the second line providing
  a breakdown of the subfields within the hierarchy, and from the third line on are the actual
  values."
  [concept-scheme csv]
  (let [all-lines (str/split-lines csv)
        sub-field-names (map keyword (-> (second all-lines)
                                         (str/replace "\"" "")
                                         (str/split #",")))
        keyword-value-strings (rest (rest all-lines))
        keyword-values (map (fn [value-as-str] (zipmap sub-field-names
                                                       (-> value-as-str
                                                           (str/replace "\"" "")
                                                           (str/split #","))))
                            keyword-value-strings)
        ]
    (cmr.common.dev.capture-reveal/capture keyword-value-strings)
    (cmr.common.dev.capture-reveal/capture keyword-values)

    (debug (str "keyword-values are " (first keyword-values)))
    ;; Filter out any values that are not leaf nodes
    (remove #(str/blank? ((leaf-node-field concept-scheme) %)) keyword-values)))

(comment
  (get-by-concept-scheme {:system (cmr.search.system/create-system)} "providers")
  (get-provider-hierarchy {:system (cmr.search.system/create-system)})
  )

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

