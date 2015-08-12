(ns cmr.umm-spec.util
  "This contains utilities for the UMM Spec code."
  (:require [cheshire.core :as json]
            [cheshire.factory :as factory]))

(defn load-json-resource
  "Loads a json resource from the classpath. The JSON file may contain comments which are ignored"
  [json-resource]
  (binding [factory/*json-factory* (factory/make-json-factory
                                     {:allow-comments true})]
    (json/decode (slurp json-resource) true)))

