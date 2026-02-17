(ns cmr.search.config
  (:require [cmr.common.config :refer [defconfig]]))

(defconfig enable-non-operational-collection-filter
  "When true, collection searches will by default exclude non-operational
   collections (those with CollectionProgress of PLANNED, DEPRECATED, PREPRINT,
   or INREVIEW) unless the caller explicitly provides a collection-progress
   parameter or passes include-non-operational=true."
  {:default false
   :type Boolean})
