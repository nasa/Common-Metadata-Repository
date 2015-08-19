(ns cmr.indexer.data.concepts.platform
  "Contains functions for converting platform hierarchies into elastic documents"
  (:require [clojure.string :as str]
            [cmr.common-app.services.kms-fetcher :as kf]))

;; TODO will need to add something similar for searching platforms

; (defn platform->keywords
;   "Converts a platform into a vector of terms for keyword searches"
;   [platform]
;   (let [{:keys [category topic term variable-level-1 variable-level-2 variable-level-3
;                 detailed-variable]} platform]
;     [category topic term variable-level-1 variable-level-2 variable-level-3 detailed-variable]))

; (defn platforms->keywords
;   "Converts the platforms into a sequence of terms for keyword searches"
;   [collection]
;   (mapcat platform->keywords (:platforms collection)))

(defn platform-short-name->elastic-doc
  "Converts a platform into the portion going in an elastic document"
  [context short-name]
  (let [full-platform (kf/get-full-hierarchy-for-short-name context :platforms short-name)
        {:keys [category series-entity long-name]} full-platform]
    {:category category
     :category.lowercase (when category (str/lower-case category))
     :series-entity series-entity
     :series-entity.lowercase (when series-entity (str/lower-case series-entity))
     :short-name short-name
     :short-name.lowercase (str/lower-case short-name)
     :long-name long-name
     :long-name.lowercase (when long-name (str/lower-case long-name))}))

(defn get-nested-elastic-docs-for-short-names
  "Converts a list of platform short names into a list of elastic documents"
  [context short-names]
  (map #(platform-short-name->elastic-doc context %) short-names))

;; I don't think this is needed - I _think_ this was so flat facets could return these
; (defn platform->facet-fields
;   [platform]
;   (let [{:keys [category topic term variable-level-1 variable-level-2
;                 variable-level-3 detailed-variable]} platform]
;     {:category category
;      :topic topic
;      :term term
;      :variable-level-1 variable-level-1
;      :variable-level-2 variable-level-2
;      :variable-level-3 variable-level-3
;      :detailed-variable detailed-variable}))

; (defn platforms->facet-fields
;   "Returns a map of the platform values in the collection for faceting storage"
;   [collection]
;   (reduce (fn [elastic-doc platform]
;             (merge-with (fn [values v]
;                           (if v
;                             (conj values v)
;                             values))
;                         elastic-doc
;                         (platform->facet-fields platform)))
;           {:category []
;            :topic []
;            :term []
;            :variable-level-1 []
;            :variable-level-2 []
;            :variable-level-3 []
;            :detailed-variable []}
;           (:platforms collection)))

