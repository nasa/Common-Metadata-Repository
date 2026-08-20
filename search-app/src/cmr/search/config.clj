(ns cmr.search.config
  (:require [cmr.common.config :refer [defconfig]]))

(defconfig enable-non-operational-collection-filter
  "When true, collection searches will by default exclude non-operational
   collections (those with CollectionProgress of PLANNED, DEPRECATED, PREPRINT,
   or INREVIEW) unless the caller explicitly provides a collection-progress
   parameter or passes include-non-operational=true."
  {:default false
   :type Boolean})

(defconfig semantic-search-enabled
  "Enables the prototype semantic collection search proxy."
  {:default false
   :type Boolean})

(defconfig semantic-search-url
  "Internal base URL of semantic-search-app."
  {:default "http://semantic-search-app:8080"})

(defconfig semantic-search-timeout-ms
  "Connection and response timeout for semantic-search-app."
  {:default 10000
   :type Long})
