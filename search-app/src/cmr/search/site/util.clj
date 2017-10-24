(ns cmr.search.site.util
  "Search site support functions."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [cmr.common-app.static :as static]
   [cmr.common.log :refer :all]
   [cmr.transmit.config :as transmit])
  (:import (java.net ConnectException)))

(def supported-directory-tags
  "A utility data structure used for both the tag names themselves as well as a
  mapping to more human-friendly version of a tag."
  {"gov.nasa.eosdis" "EOSDIS"})

(defn get-app-url
  "A utility function for getting the app's root URL.

  When called from the CLI, the key `:cmr-application` will have a value
  matching the application (in this case, `:search`). When called from a
  running system, the request context will be in place and a null value will
  be returned when applying the `:cmr-application` key."
  [context]
  (transmit/application-public-root-url
    (or (:cmr-application context) context)))

(defn get-search-reference-file
  "Locate a reference file whose location is well-known and stable (won't
  change) in the search-app codebase. This is intended to be used to calculate
  the absolute file-system path to the `search-app` directory.

  Note, seleection of function depends on ns being in `require`, so it can't
  be something that causes a cyclic dependency."
  []
  (-> (meta #'cmr.search.site.util/get-search-reference-file)
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
      (walk-parents 5)
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

(defn endpoint-get
  "A utility function that performs an HTTP `GET` and a few consistently used
  processing steps."
  [& args]
  (try
    (-> (apply client/get args)
        :body
        (json/parse-string true))
    (catch ConnectException e
           (warn (.getMessage e))
           {})))
