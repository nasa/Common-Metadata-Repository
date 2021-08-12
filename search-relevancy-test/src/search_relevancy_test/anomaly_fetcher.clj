(ns search-relevancy-test.anomaly-fetcher
  "Functions to support downloading collections from the anomaly file."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [debug error info warn]]
   [cmr.common.util :as util]
   [search-relevancy-test.core :as core]))

(defconfig source-url
  "Base URL for retrieving anomaly concepts."
  {:default "https://cmr.earthdata.nasa.gov/search"})

(def format-string->directory
  "Map of metadata-format to the directory name where those formats are saved."
  {"application/echo10+xml" "echo10"
   "application/dif+xml" "dif"
   "application/dif10+xml" "dif10"
   "application/iso:smap+xml" "iso-smap"
   "application/iso19115+xml" "iso19115"
   "application/vnd.nasa.cmr.umm+json" "umm-json"})

(defn- find-collection-ids-to-download
  "Returns a list of concepts that need to be downloaded. Uses the anomaly tests CSV file."
  ([]
   (find-collection-ids-to-download core/provider-anomaly-filename))
  ([filename]
   (let [anomaly-tests (core/read-anomaly-test-csv filename)
         concept-ids (mapcat (fn [anomaly-test]
                               (string/split (:concept-ids anomaly-test) #","))
                             anomaly-tests)]
     (distinct concept-ids))))

(defn- formats-for-collections
  "Returns a map of concept-id to the metadata format for that collection."
  [concept-ids]
  (let [url (str (source-url) "/collections.umm-json")
        options (util/remove-nil-keys
                 {:form-params {:concept-id concept-ids
                                :page-size 2000}
                  :headers {:authorization (System/getenv "CMR_SYSTEM_TOKEN")}})
        response (client/post url options)
        items (:items (json/parse-string (:body response) true))]
    (into {}
          (for [item items
                :let [meta (:meta item)]]
            [(:concept-id meta) (:format meta)]))))

(defn- download-concept-metadata
  "Returns the metadata for the provided concept-id in its native format."
  [concept-id fmt]
  (let [url (format "%s/concepts/%s" (source-url) concept-id)
        params (util/remove-nil-keys
                {:query-params {:concept-id concept-id}
                 :headers {:authorization (System/getenv "CMR_SYSTEM_TOKEN")}})
        params (if (= fmt "application/vnd.nasa.cmr.umm+json")
                 (assoc params :accept "application/vnd.nasa.cmr.umm+json") ; want latest version of umm
                 params)
        response (client/get url params)]
    (:body response)))

(def collections-dir
  "The place to save collections."
  "../search-relevancy-test/resources/test_files")

(defn- file-extension
  "Given a format, return the file extension"
  [format]
  (if (= "application/vnd.nasa.cmr.umm+json" format)
    ".json"
    ".xml"))

(defn download-and-save-all-collections
  "Main function for this namespace to find all the concepts to download, retrieve them from
  the operational environment, and save them into the appropriate local testing directory."
  ([]
   (download-and-save-all-collections core/provider-anomaly-filename))
  ([filename]
   (let [concept-ids (find-collection-ids-to-download filename)
         concept-id->format-map (formats-for-collections concept-ids)]
     (doseq [[concept-id fmt] concept-id->format-map]
       (let [directory (get format-string->directory fmt)
             filename (str concept-id (file-extension fmt))
             full-path (format "%s/%s/%s" collections-dir directory filename)]
         (if (.exists (io/as-file full-path))
           (debug "Skipping already downloaded concept" concept-id)
           (let [collection-metadata (download-concept-metadata concept-id fmt)]
             (info "Saving" filename "to" directory)
             (spit full-path collection-metadata))))))))

(comment
 (download-and-save-all-collections core/edsc-anomaly-filename)
 (find-collection-ids-to-download core/edsc-anomaly-filename)
 (formats-for-collections ["C1200196931-SCIOPS" "C1000000803-DEV08"]) ;; SIT
 (formats-for-collections ["C1397496671-NSIDC_ECS"]) ;; Prod
 (download-concept-metadata "C1237114193-GES_DISC"))
