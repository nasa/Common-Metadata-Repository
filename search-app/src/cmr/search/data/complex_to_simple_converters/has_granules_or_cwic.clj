(ns cmr.search.data.complex-to-simple-converters.has-granules-or-cwic
  (:require
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.search.models.query :as qm]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as has-granules-or-cwic-base]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-base]))

;; The following protocol implementation ensures that a c.s.m.q.HasGranulesOrCwicCondition record in our
;; query model will be expanded into a form which can, in turn, be converted into another structure
;; in our query model which can, once again, be converted into an Elasticsearch query.

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrCwicCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections tagged CWIC,
    ;; so we will use the has-granules-or-cwic-map and get the keys (concept IDs) of entries with true values.
    (let [has-granules-or-cwic-map (has-granules-or-cwic-base/get-has-granules-or-cwic-map context)
          has-granules-map (has-granules-base/get-has-granules-map context)
          concept-ids (map key (filter val has-granules-map))
          condition (cqm/string-conditions :concept-id concept-ids true)
          or-cwic-concept-ids (map key (filter val has-granules-or-cwic-map))
          or-cwic-condition (cqm/string-conditions :concept-id or-cwic-concept-ids true)]
      (if (:has-granules-or-cwic this)
        or-cwic-condition
        (cqm/negated-condition condition)))))
