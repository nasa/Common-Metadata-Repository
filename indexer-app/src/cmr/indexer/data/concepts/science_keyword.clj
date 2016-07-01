(ns cmr.indexer.data.concepts.science-keyword
  "Contains functions for converting science keyword domains into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common.util :as util]
            [cmr.common-app.services.kms-fetcher :as kf]))

(defn flatten-science-keywords
  "Convert the science keywords into a flat list composed of the category, topic, and term values."
  [collection]
  (distinct (mapcat (fn [science-keyword]
                      (let [{:keys [category topic term]} science-keyword]
                        (filter identity [category topic term])))
                    (:science-keywords collection))))

(defn science-keyword->keywords
  "Converts a science keyword into a vector of terms for keyword searches"
  [science-keyword]
  (let [{:keys [category topic term variable-level-1 variable-level-2 variable-level-3
                detailed-variable]} science-keyword]
    [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]))

(defn science-keywords->keywords
  "Converts the science keywords into a sequence of terms for keyword searches"
  [collection]
  (mapcat science-keyword->keywords (:science-keywords collection)))

(defn- normalize-sk-field-value
  "Convert science keyword field values into upper case and trim whitespace from both ends."
  [sk-field-value]
  (when sk-field-value
    (-> sk-field-value str/trim str/upper-case)))

(defn science-keyword->elastic-doc
  "Converts a science keyword into the portion going in an elastic document. If there is a match
  with the science keywords in KMS we also index the UUID from KMS. We index all of the science
  keyword fields in all caps since GCMD enforces all caps when adding keywords to KMS. Note that
  this means there is no need to also index the keywords in all lowercase; however, we continue to
  index in lowercase so that science keywords are not treated as a special case in parts of the
  code that use lowercase mappings."
  [gcmd-keywords-map science-keyword]
  (let [science-keyword-upper-case (util/map-values normalize-sk-field-value science-keyword)
        {:keys [category topic term variable-level-1 variable-level-2 variable-level-3
                detailed-variable]} science-keyword-upper-case
        {:keys [uuid]} (kf/get-full-hierarchy-for-science-keyword gcmd-keywords-map science-keyword)]
    {:category category
     :category.lowercase (str/lower-case category)
     :topic topic
     :topic.lowercase (str/lower-case topic)
     :term term
     :term.lowercase (str/lower-case term)
     :variable-level-1 variable-level-1
     :variable-level-1.lowercase (when variable-level-1 (str/lower-case variable-level-1))
     :variable-level-2 variable-level-2
     :variable-level-2.lowercase (when variable-level-2 (str/lower-case variable-level-2))
     :variable-level-3 variable-level-3
     :variable-level-3.lowercase (when variable-level-3 (str/lower-case variable-level-3))
     :detailed-variable detailed-variable
     :detailed-variable.lowercase (when detailed-variable (str/lower-case detailed-variable))
     :uuid uuid
     :uuid.lowercase (when uuid (str/lower-case uuid))}))

(defn humanized-science-keyword->elastic-doc
  "Extracts humanized fields from the science keyword and places them into an elastic doc with
  the same shape/keys as science-keyword->elastic-doc."
  [science-keyword]
  (let [humanized-fields (filter #(-> % key namespace (= "cmr.humanized")) science-keyword)
        ns-stripped-fields (util/map-keys #(keyword (name %)) humanized-fields)]
    (merge
     ns-stripped-fields
     ;; Create "*.lowercase" versions of the fields
     (->> ns-stripped-fields
          (util/map-keys #(keyword (str (name %) ".lowercase")))
          (util/map-values #(when % (str/lower-case %)))))))

(defn science-keyword->facet-fields
  [science-keyword]
  (let [science-keyword-upper-case (util/map-values normalize-sk-field-value science-keyword)
        {:keys [category topic term variable-level-1 variable-level-2
                variable-level-3 detailed-variable]} science-keyword-upper-case]
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
          (:science-keywords collection)))
