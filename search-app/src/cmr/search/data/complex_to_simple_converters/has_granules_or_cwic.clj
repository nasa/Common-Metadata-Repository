(ns cmr.search.data.complex-to-simple-converters.has-granules-or-cwic
  (:require
   [cmr.common-app.services.search.complex-to-simple :as c2s]
   [cmr.common-app.services.search.query-model :as cqm]
   [cmr.search.models.query :as qm]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as has-gran-or-cwic-base]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-base]))

;; The following protocol implementation ensures that a c.s.m.q.HasGranulesOrCwicCondition record in our
;; query model will be expanded into a form which can, in turn, be converted into another structure
;; in our query model which can, once again, be converted into an Elasticsearch query.

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrCwicCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections with CWIC consortium,
    ;; so we will use the has-granules-or-cwic-map and get the keys (concept IDs) of entries with true values.
    (let [has-granules-or-cwic-map (has-gran-or-cwic-base/get-has-granules-or-cwic-map context)
          has-granules-map (has-granules-base/get-has-granules-map context)
          concept-ids (map key (filter val has-granules-map))
          condition (if (seq concept-ids)
                      (cqm/string-conditions :concept-id concept-ids true)
                      ;; when concept-ids are empty, string-conditions throw internal errors.
                      ;; replace it with a non-existing concept-id to make sure nothing is returned using the condition..
                      cqm/match-none)
          or-cwic-concept-ids (map key (filter val has-granules-or-cwic-map))
          or-cwic-condition (if (seq or-cwic-concept-ids)
                              (cqm/string-conditions :concept-id or-cwic-concept-ids true)
                              cqm/match-none)]
      (if (:has-granules-or-cwic this)
        or-cwic-condition
        (cqm/negated-condition condition)))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrOpenSearchCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections with opensearch consortium,
    ;; i.e. when consortiums contain one or more of the following: CWIC,FEDEO,GEOSS,CEOS,EOSDIS.
    ;; so we will use the has-granules-or-opensearch-map and get the keys (concept IDs) of entries with true values.
    (let [has-granules-or-opensearch-map (has-gran-or-cwic-base/get-has-granules-or-opensearch-map context)
          has-granules-map (has-granules-base/get-has-granules-map context)
          concept-ids (map key (filter val has-granules-map))
          condition (if (seq concept-ids)
                      (cqm/string-conditions :concept-id concept-ids true)
                      ;; when concept-ids are empty, string-conditions throw internal errors.
                      ;; replace it with a non-existing concept-id to make sure nothing is returned using the condition..
                      cqm/match-none)
          or-opensearch-concept-ids (map key (filter val has-granules-or-opensearch-map))
          or-opensearch-condition (if (seq or-opensearch-concept-ids)
                                    (cqm/string-conditions :concept-id or-opensearch-concept-ids true)
                                    cqm/match-none)]
      (if (:has-granules-or-opensearch this)
        or-opensearch-condition
        (cqm/negated-condition condition)))))
