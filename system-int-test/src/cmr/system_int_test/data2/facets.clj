(ns cmr.system-int-test.data2.facets
  "Contains functions for dealing with facets"
  (:require [cmr.common.xml :as cx]
            [clojure.set :as set]
            [camel-snake-kebab.core :as csk]
            [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.search.services.query-execution.facets.facets-results-feature :as frf]))

(defn- parse-facet-xml
  "Converts an XML facet element into a nested map representation."
  [facet-elem]
  (let [field (get-in facet-elem [:attrs :field])
        value-elems (cx/elements-at-path facet-elem [:value])
        value-count-map-elems (cx/elements-at-path facet-elem [:value-count-maps :value-count-map])
        facet (cx/element-at-path facet-elem [:facet])]
    (cond (seq value-count-map-elems)
          {(keyword field) (for [value-count-map-elem value-count-map-elems
                                 :let [value-elem (cx/element-at-path value-count-map-elem [:value])
                                       sub-facet-elem (cx/element-at-path value-count-map-elem [:facet])]]
                             (merge (when sub-facet-elem
                                      (merge (parse-facet-xml sub-facet-elem)
                                             {:subfields [(get-in sub-facet-elem [:attrs :field])]}))
                                    {:value (first (:content value-elem))
                                     :count (Long. ^String (get-in value-elem [:attrs :count]))}))}
          facet
          (merge (parse-facet-xml facet)
                 {:field field
                  :subfields [(get-in facet [:attrs :field])]})

          (not ((csk/->kebab-case-keyword field) kf/nested-fields-mappings))
          {:field field
           :value-counts (for [value-elem value-elems]
                           [(first (:content value-elem))
                            (Long. ^String (get-in value-elem [:attrs :count]))])}
          :else
          {:field field
           :subfields []})))

(defn parse-facets-xml
  "Converts an XML facets element into a nested map representation."
  [facets-elem]
  (when facets-elem
    (when-let [facet-elems (cx/elements-at-path facets-elem [:facet])]
      (for [facet-elem facet-elems]
        (parse-facet-xml facet-elem)))))

(def echo-facet-key->cmr-facet-name
  (set/map-invert frf/cmr-facet-name->echo-facet-keyword))

(defn- parse-echo-facet-xml
  "Convert a echo facet xml element into a facet map containing field and value counts"
  [facet-elem]
  (let [elem-name (:tag facet-elem)
        value-elems (cx/elements-at-path facet-elem [elem-name])
        value-counts (for [value-elem value-elems]
                       [(cx/string-at-path value-elem [:term])
                        (cx/long-at-path value-elem [:count])])]
    {:field (echo-facet-key->cmr-facet-name elem-name)
     :value-counts value-counts}))

(defn parse-echo-facets-xml
  "Converts an xml element in ECHO format into a sequence of facet maps containing field and value counts"
  [facets-elem]
  (map parse-echo-facet-xml (:content facets-elem)))

