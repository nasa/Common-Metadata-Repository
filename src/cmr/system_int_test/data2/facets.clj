(ns cmr.system-int-test.data2.facets
  "Contains functions for dealing with facets"
  (:require [cmr.common.xml :as cx]))

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