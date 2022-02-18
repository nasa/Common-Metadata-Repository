(ns cmr.indexer.data.concepts.collection.humanizer
  "Contains functions to converting collection into elasticsearch humanized collection docs"
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common-app.humanizer :as humanizer]
   [cmr.common-app.services.kms-fetcher :as kms-fetcher]
   [cmr.indexer.data.concepts.collection.platform :as platform]
   [cmr.indexer.data.concepts.collection.science-keyword :as sk]
   [cmr.indexer.data.humanizer-fetcher :as humanizer-fetcher]))

(defn- add-humanized-lowercase
  "Adds a :value-lowercase field to a humanized object"
  [obj]
  (assoc obj :value-lowercase (str/lower-case (:value obj))))

(defn- select-indexable-humanizer-fields
  "Selects the fields from humanizers that can be indexed."
  [value]
  (select-keys value [:value :priority]))

(defn- extract-humanized-elastic-fields
  "Descends into the humanized collection extracting values at the given humanized
  field path and returns a map of humanized and lowercase humanized elastic fields
  for that path"
  [humanized-collection path base-es-field]
  (let [prefix (subs (str base-es-field) 1)
        field (keyword (str prefix "-humanized"))
        value-with-priorities (util/get-in-all humanized-collection path)
        value-with-priorities (if (sequential? value-with-priorities)
                                (map select-indexable-humanizer-fields value-with-priorities)
                                (select-indexable-humanizer-fields value-with-priorities))
        value-with-lowercases (if (sequential? value-with-priorities)
                                (map add-humanized-lowercase
                                     (distinct (filter :value value-with-priorities)))
                                (add-humanized-lowercase value-with-priorities))]
    {field value-with-lowercases}))

(defn collection-humanizers-elastic
  "Given a umm-spec collection, returns humanized elastic search fields"
  [context collection]
  (let [humanized (humanizer/umm-collection->umm-collection+humanizers
                    collection (humanizer-fetcher/get-humanizer-instructions context))
        extract-fields (partial extract-humanized-elastic-fields humanized)
        kms-index (kms-fetcher/get-kms-index context)
        platforms2-humanized (map #(platform/humanized-platform2-nested-fields->elastic-doc kms-index %)
                                  (:Platforms humanized))]
    (merge
      {:science-keywords-humanized (map sk/humanized-science-keyword->elastic-doc
                                        (:ScienceKeywords humanized))}
      {:platforms2-humanized platforms2-humanized}
      (extract-fields [:ArchiveAndDistributionInformation
                                        :FileDistributionInformation
                                        :cmr-humanized/Format]
                                       :granule-data-format)
      (extract-fields [:Platforms :cmr-humanized/ShortName] :platform-sn)
      (extract-fields [:Platforms :Instruments :cmr-humanized/ShortName] :instrument-sn)
      (extract-fields [:Projects :cmr-humanized/ShortName] :project-sn)
      (extract-fields [:ProcessingLevel :cmr-humanized/Id] :processing-level-id)
      (extract-fields [:DataCenters :cmr-humanized/ShortName] :organization))))
