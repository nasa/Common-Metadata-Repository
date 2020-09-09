(ns cmr.search.services.query-execution.facets.cycle-facets
  "Functions for generating the spatial facets within v2 granule 
  facets. Creates aggregations for cycles, further aggregated by 
  pass.")

(def aggregate-by-concept-id {:concept-id
                              {:terms {:field :concept-id
                                       :size 1}}})

(def aggregate-by-cycle {:histogram
                         {:field :cycle
                          :min_doc_count 1
                          :interval 1}
                         :aggs aggregate-by-concept-id})

(def aggregate-by-pass {:nested {:path "passes"}
                        :aggs {:pass
                               {:histogram
                                {:field "passes.pass"
                                 :min_doc_count 1
                                 :interval 1}}
                               :aggs
                               {:reverse_nested {}
                                :aggs aggregate-by-concept-id}}})

(defn query-params->cycle-facet-aggs
  "Returns the correct level of a cycle-pass query depending on if a 
  cycle param has been passed."
  [query-params]
  (let [field-regex (re-pattern "cycle.*")
        cycle-params (keep (fn [[k]]
                             (when (re-matches field-regex k) k))
                           query-params)]
    (if (= "cycle[]" (first cycle-params))
      :passes
      :cycle)))

(defn cycle-facet
  "Creates a cycle aggregate query for cycle -> pass"
  [query-params]
  (let [level (query-params->cycle-facet-aggs query-params)]
    (if (= :passes level)
      aggregate-by-pass
      aggregate-by-cycle)))

