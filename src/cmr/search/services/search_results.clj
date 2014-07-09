(ns cmr.search.services.search-results
  "Contains functions for validating search results requested formats and for converting to
  requested format"
  (:require
            [clojure.data.xml :as x]
            [clojure.set :as set]

            [clojure.string :as s]
            [cmr.common.xml :as cx]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as ct]
            [cmr.common.mime-types :as mt]
            [cmr.search.models.results :as r]

            )
  (:import
    [java.io StringWriter]))


;; TODO move what's left of this namespace into the query service

(defmulti search-results->response
  "TODO document me"
  (fn [context query results]
    (:result-format query)))

