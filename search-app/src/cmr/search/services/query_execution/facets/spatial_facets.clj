(ns cmr.search.services.query-execution.facets.spatial-facets
  "Functions for generating the spatial facets within v2 granule facets.")

(defn spatial-facet
  "Creates a spatial facet for the provided field."
  [_query-params]
  {:histogram
          {:field :cycle
           :min_doc_count 1
           :interval 1}
          :aggs {:passes
                 {:nested {:path "passes"}
                  :aggs {:pass {:histogram {:field "passes.pass"
                                            :min_doc_count 1
                                            :interval 1}}}}}})

