(ns cmr.umm-spec.test.umm-record-sanitizer
  "This contains functions for manipulating the generator generated umm-record to a sanitized
  version to pass the xml validation for various supported metadata format. This is needed because
  the incompatibility between UMM JSON schema and schemas of the various metadata formats making the
  generated metadata xml invalid without some kind of sanitization."
  (:require [clj-time.core :as t]
            [clj-time.format :as f]
            [cmr.common.util :as util :refer [update-in-each]]
            [cmr.umm-spec.util :as su]
            [cmr.umm-spec.json-schema :as js]
            [cmr.umm-spec.models.collection :as umm-c]
            [cmr.umm-spec.models.common :as cmn]
            [cmr.umm-spec.umm-to-xml-mappings.dif10 :as dif10]
            [cmr.umm-spec.umm-to-xml-mappings.echo10.spatial :as echo10-spatial-gen]
            [cmr.umm-spec.xml-to-umm-mappings.echo10.spatial :as echo10-spatial-parse]))

(defn- set-if-exist
  "Sets the field of the given record to the value if the field has a value, returns the record."
  [record field value]
  (if (field record)
    (assoc record field value)
    record))

(defn sanitized-umm-record
  "Returns the sanitized version of the given umm record."
  [record]
  (-> record
      ;; DataLanguage should be from a list of enumerations which are not defined in UMM JSON schema
      ;; so here we just replace the generated value to English to make it through the validation.
      (set-if-exist :DataLanguage "English")
      (set-if-exist :CollectionProgress "COMPLETE")))