(ns cmr.ingest.services.messages
  (:require [clojure.string :as str]
            [cmr.common.services.errors :as errors]))

(defn parent-collection-does-not-exist [granule-ur]
  (format
    "Parent collection for granule [%s] does not exist."
    granule-ur))