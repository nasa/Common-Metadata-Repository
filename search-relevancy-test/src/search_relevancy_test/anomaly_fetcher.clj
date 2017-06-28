(ns search-relevancy-test.anomaly-fetcher
  "Functions to support downloading collections from the anomaly file."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.log :refer [debug error info warn]]
   [search-relevancy-test.core :as core]))

(defconfig source-url
  "Base URL for retrieving anomaly concepts."
  {:default "https://cmr.earthdata.nasa.gov/search"})

(def format-string->directory
  "Map of metadata-format to the directory name where those formats are saved. TODO - add umm-json."
  {"application/echo10+xml" "echo10"
   "application/dif+xml" "dif9"
   "application/dif10+xml" "dif10"
   "application/iso:smap+xml" "iso-smap"
   "application/iso-19115+xml" "iso-19115"})

(defn- find-collection-ids-to-download
  "Returns a list of concepts that need to be downloaded. Uses the anomaly tests CSV file."
  []
  (let [anomaly-tests (core/read-anomaly-test-csv)
        concept-ids (mapcat (fn [anomaly-test]
                              (string/split (:concept-ids anomaly-test) #","))
                            anomaly-tests)]
    (distinct concept-ids)))

(defn- formats-for-collections
  "Returns a map of concept-id to the metadata format for that collection."
  [concept-ids]
  (let [url (str (source-url) "/collections.umm-json")
        response (client/get url {:query-params {:concept-id concept-ids}})
        items (:items (json/parse-string (:body response) true))]
    (into {}
          (for [item items
                :let [meta (:meta item)]]
            [(:concept-id meta) (:format meta)]))))

(defn- download-concept-metadata
  "Returns the metadata for the provided concept-id in its native format."
  [concept-id]
  (let [url (format "%s/concepts/%s" (source-url) concept-id)
        response (client/get url {:query-params {:concept-id concept-id}})]
    (:body response)))

(def collections-dir
  "The place to save collections."
  "../search-relevancy-test/resources/test_files")

(defn download-and-save-all-collections
  "Main function for this namespace to find all the concepts to download, retrieve them from
  the operational environment, and save them into the appropriate local testing directory."
  []
  (let [concept-ids (find-collection-ids-to-download)
        concept-id->format-map (formats-for-collections concept-ids)]
    (doseq [[concept-id fmt] concept-id->format-map]
      (let [directory (get format-string->directory fmt)
            filename (str concept-id ".xml")
            full-path (format "%s/%s/%s" collections-dir directory filename)]
        (if (.exists (io/as-file full-path))
          (info "Skipping already downloaded concept" concept-id)
          (let [collection-metadata (download-concept-metadata concept-id)]
            (info "Saving" filename "to" directory)
            (spit full-path collection-metadata)))))))


(comment
 (download-and-save-all-collections)
 (formats-for-collections ["C1200196931-SCIOPS" "C1000000803-DEV08"]) ;; SIT
 (formats-for-collections ["C1000001282-NSIDC_ECS" "C1344054559-NSIDC_ECS"]) ;; Prod
 (download-concept-metadata "C1200196931-SCIOPS"))
