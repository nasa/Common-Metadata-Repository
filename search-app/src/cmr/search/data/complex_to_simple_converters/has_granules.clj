(ns cmr.search.data.complex-to-simple-converters.has-granules
  (:require
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.search.models.query :as qm]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-base]))

;; The following protocol implementation ensures that a c.s.m.q.HasGranulesCondition record in our
;; query model will be expanded into a form which can, in turn, be converted into another structure
;; in our query model which can, once again, be converted into an Elasticsearch query.

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules, so we will use the
    ;; has-granules-map and get the keys (concept IDs) of entries with true values.
    (let [has-granules-map (has-granules-base/get-has-granules-map context)
          concept-ids (map key (filter val has-granules-map))
          condition (cqm/string-conditions :concept-id concept-ids true)]
      (if (:has-granules this)
        condition
        (cqm/negated-condition condition)))))
