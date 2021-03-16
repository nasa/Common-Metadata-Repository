(ns cmr.search.data.complex-to-simple-converters.has-granules-or-opensearch
  (:require
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.search.models.query :as qm]
   [cmr.search.services.query-execution.has-granules-or-opensearch-results-feature
    :as has-granules-or-opensearch-base]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-base]))

;; The following protocol implementation ensures that a c.s.m.q.HasGranulesOrOpenSearchCondition record in our
;; query model will be expanded into a form which can, in turn, be converted into another structure
;; in our query model which can, once again, be converted into an Elasticsearch query.

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrOpenSearchCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections tagged OpenSearch,
    ;; so we will use the has-granules-or-opensearch-map and get the keys (concept IDs) of entries with true values.
    (let [has-granules-or-opensearch-map (has-granules-or-opensearch-base/get-has-granules-or-opensearch-map context)
          has-granules-map (has-granules-base/get-has-granules-map context)
          concept-ids (map key (filter val has-granules-map))
          condition (cqm/string-conditions :concept-id concept-ids true)
          or-opensearch-concept-ids (map key (filter val has-granules-or-opensearch-map))
          or-opensearch-condition (cqm/string-conditions :concept-id or-opensearch-concept-ids true)]
      (if (:has-granules-or-opensearch this)
        or-opensearch-condition
        (cqm/negated-condition condition)))))
