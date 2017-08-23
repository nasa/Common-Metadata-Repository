(ns cmr.search.site.util
  "Search site support functions."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.search.site.data :as data]
   [cmr.transmit.config :as transmit]))

(defn get-search-reference-file
  "Locate a reference file whose location is well-known and stable (won't
  change) in the search-app codebase. This is intended to be used to calculate
  the absolute file-system path to the `search-app` directory."
  []
  (-> (meta #'cmr.search.system/start)
      :file
      (io/resource)
      (.getFile)))

(defn walk-parents
  "A utility function for getting successive parent directories of a given path.
  The intent is to use this with absolute paths, not relative paths."
  [file-path depth]
  (loop [result file-path
         d depth]
    (if (zero? d)
      result
      (-> result
          (io/file)
          (.getParent)
          (recur (dec d))))))

(defn get-search-app-abs-path
  "A utility function used to locate the absolute path of the `search-app`
  directory. Intended to be used to define the correct `resources/public`
  location, even when called from another application besides `search` (e.g.,
  `dev-system`)."
  []
  (-> (get-search-reference-file)
      (walk-parents 4)
      (str "/")))

(defn make-relative-parents
  "A utility function for creating relative-path parent URLs for a given
  depth."
  [depth]
  (loop [result ""
         d depth]
    (if (zero? d)
      result
      (-> result
          (str "../")
          (recur (dec d))))))

(defn get-provider-resource
  "Utility functions that define the string value for static ouput files. These
  are intended to be used to create absolute paths to specific files."
  [base provider-id tag filename]
  (format "%s/resources/public/site/collections/directory/%s/%s/%s"
          base
          provider-id
          tag
          filename))

(defn get-provider-sitemap
  "Get the absolute file-system path for a provider+tag sitemap.xml resource."
  [base provider-id tag]
  (get-provider-resource base provider-id tag "sitemap.xml"))

(defn get-provider-index
  "Get the absolute file-system path for a provider+tag index.html resource."
  [base provider-id tag]
  (get-provider-resource base provider-id tag "index.html"))
