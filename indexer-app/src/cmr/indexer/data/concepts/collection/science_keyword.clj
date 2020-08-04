(ns cmr.indexer.data.concepts.collection.science-keyword
  "Contains functions for converting science keyword domains into elastic documents"
  (:require
    [clojure.string :as str]
    [cmr.common-app.services.kms-fetcher :as kf]
    [cmr.common-app.services.kms-lookup :as kms-lookup]
    [cmr.common.util :as util]
    [cmr.umm-spec.util :as spec-util]))

(defn flatten-science-keywords
  "Convert the science keywords into a flat list composed of the category, topic, and term values."
  [collection]
  (distinct (mapcat (fn [science-keyword]
                      (let [{category :Category topic :Topic term :Term} science-keyword]
                        (filter identity [category topic term])))
                    (:ScienceKeywords collection))))

(defn- normalize-sk-field-value
  "Convert science keyword field values into upper case and trim whitespace from both ends."
  [sk-field-value]
  (when (and sk-field-value (not= spec-util/not-provided sk-field-value))
    (-> sk-field-value str/trim str/upper-case)))

(defn science-keyword->elastic-doc
  "Converts a science keyword into the portion going in an elastic document. If there is a match
  with the science keywords in KMS we also index the UUID from KMS. We index all of the science
  keyword fields in all caps since GCMD enforces all caps when adding keywords to KMS. Note that
  this means there is no need to also index the keywords in all lowercase; however, we continue to
  index in lowercase so that science keywords are not treated as a special case in parts of the
  code that use lowercase mappings."
  [kms-index science-keyword]
  (let [science-keyword-kebab-key (util/map-keys->kebab-case science-keyword)
        science-keyword-upper-case (util/map-values normalize-sk-field-value
                                                    science-keyword-kebab-key)
        {:keys [category topic term variable-level-1 variable-level-2 variable-level-3
                detailed-variable]} science-keyword-upper-case
        {:keys [uuid]} (kms-lookup/lookup-by-umm-c-keyword kms-index :science-keywords
                                                           science-keyword-kebab-key)]
    {:category category
     :category-lowercase (util/safe-lowercase category)
     :topic topic
     :topic-lowercase (util/safe-lowercase topic)
     :term term
     :term-lowercase (util/safe-lowercase term)
     :variable-level-1 variable-level-1
     :variable-level-1-lowercase (util/safe-lowercase variable-level-1)
     :variable-level-2 variable-level-2
     :variable-level-2-lowercase (util/safe-lowercase variable-level-2)
     :variable-level-3 variable-level-3
     :variable-level-3-lowercase (util/safe-lowercase variable-level-3)
     :detailed-variable detailed-variable
     :detailed-variable-lowercase (util/safe-lowercase detailed-variable)
     :uuid uuid
     :uuid-lowercase (util/safe-lowercase uuid)}))

(defn humanized-science-keyword->elastic-doc
  "Extracts humanized fields from the science keyword and places them into an elastic doc with
  the same shape/keys as science-keyword->elastic-doc."
  [science-keyword]
  (let [humanized-fields (filter #(-> % key namespace (= "cmr-humanized")) science-keyword)
        humanized-fields-with-raw-values (util/map-values :value humanized-fields)
        ns-stripped-fields (util/map-keys->kebab-case humanized-fields-with-raw-values)]
    (merge
     ns-stripped-fields
     ;; Create "*-lowercase" versions of the fields
     (->> ns-stripped-fields
          (util/map-keys #(keyword (str (name %) "-lowercase")))
          (util/map-values #(util/safe-lowercase %))))))

(defn- science-keyword->facet-fields
  [science-keyword]
  (let [science-keyword-upper-case (util/map-values normalize-sk-field-value science-keyword)
        {category :Category topic :Topic term :Term variable-level-1 :VariableLevel1
         variable-level-2 :VariableLevel2 variable-level-3 :VariableLevel3
         detailed-variable :DetailedVariable} science-keyword-upper-case]
    {:category category
     :topic topic
     :term term
     :variable-level-1 variable-level-1
     :variable-level-2 variable-level-2
     :variable-level-3 variable-level-3
     :detailed-variable detailed-variable}))

(defn science-keywords->facet-fields
  "Returns a map of the science keyword values in the collection for faceting storage"
  [collection]
  (reduce (fn [elastic-doc science-keyword]
            (merge-with (fn [values v]
                          (if v
                            (conj values v)
                            values))
                        elastic-doc
                        (science-keyword->facet-fields science-keyword)))
          {:category []
           :topic []
           :term []
           :variable-level-1 []
           :variable-level-2 []
           :variable-level-3 []
           :detailed-variable []}
          (:ScienceKeywords collection)))
