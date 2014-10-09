(ns cmr.system-int-test.data2.facets
  "Contains functions for dealing with facets"
  (:require [cmr.common.xml :as cx]
            [clojure.set :as set]
            [cmr.search.services.query-execution.facets-results-feature :as frf]))

(defn parse-facets-xml
  "Converts an xml element into a sequence of facet maps containing field and value counts"
  [facets-elem]
  (when facets-elem
    (when-let [facet-elems (cx/elements-at-path facets-elem [:facet])]
      (for [facet-elem facet-elems]
        (let [field (get-in facet-elem [:attrs :field])
              value-elems (cx/elements-at-path facet-elem [:value])]
          {:field field
           :value-counts (for [value-elem value-elems]
                           [(first (:content value-elem))
                            (Long. ^String (get-in value-elem [:attrs :count]))])})))))

(def echo-facet-key->cmr-facet-name
  (set/map-invert frf/cmr-facet-name->echo-facet-keyword))

(defn- parse-echo-facet-xml
  "Convert a echo facet xml element into a facet map containing field and value counts"
  [facet-elem]
  (let [elem-name (:tag facet-elem)
        value-elems (cx/elements-at-path facet-elem [elem-name])
        value-counts (for [value-elem value-elems]
                       [(cx/string-at-path value-elem [:term])
                        (Long. (cx/string-at-path value-elem [:count]))])]
    {:field (echo-facet-key->cmr-facet-name elem-name)
     :value-counts value-counts}))

(defn parse-echo-facets-xml
  "Converts an xml element in ECHO format into a sequence of facet maps containing field and value counts"
  [facets-elem]
  (map parse-echo-facet-xml (:content facets-elem)))

