(ns cmr.search.data.complex-to-simple-converters.has-granules-or-cwic
  (:require
   [cmr.elastic-utils.search.query-transform :as c2s]
   [cmr.common.services.search.query-model :as cqm]
   [cmr.search.services.query-execution.has-granules-or-cwic-results-feature :as has-gran-or-cwic-base]
   [cmr.search.services.query-execution.has-granules-results-feature :as has-granules-base]))

(defn- create-concept-ids-condition
  "Extract concept IDs from cache map where value is true, return string-conditions or match-none."
  [cache-map]
  (let [concept-ids (map key (filter val cache-map))]
    (if (seq concept-ids)
      (cqm/string-conditions :concept-id concept-ids true)
      ;; when concept-ids are empty, string-conditions throw internal errors.
      ;; replace it with a non-existing concept-id to make sure nothing is returned using the condition.
      cqm/match-none)))

;; The following protocol implementation ensures that a c.s.m.q.HasGranulesOrCwicCondition record in our
;; query model will be expanded into a form which can, in turn, be converted into another structure
;; in our query model which can, once again, be converted into an Elasticsearch query.

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrCwicCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections with CWIC consortium,
    ;; so we will use the has-granules-or-cwic-map and get the keys (concept IDs) of entries with true values.
    (if (:has-granules-or-cwic this)
      (let [has-granules-or-cwic-map (has-gran-or-cwic-base/get-has-granules-or-cwic-map context)]
        (create-concept-ids-condition has-granules-or-cwic-map))
      (let [has-granules-map (has-granules-base/get-has-granules-map context)]
        (cqm/negated-condition (create-concept-ids-condition has-granules-map))))))

(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.HasGranulesOrOpenSearchCondition
  (c2s/reduce-query-condition [this context]
    ;; We need to limit the query to collections which have granules or to collections with opensearch consortium,
    ;; i.e. when consortiums contain one or more of the following: CWIC,FEDEO,GEOSS,CEOS,EOSDIS.
    ;; so we will use the has-granules-or-opensearch-map and get the keys (concept IDs) of entries with true values.
    (if (:has-granules-or-opensearch this)
      (let [has-granules-or-opensearch-map (has-gran-or-cwic-base/get-has-granules-or-opensearch-map context)]
        (create-concept-ids-condition has-granules-or-opensearch-map))
      (let [has-granules-map (has-granules-base/get-has-granules-map context)]
        (cqm/negated-condition (create-concept-ids-condition has-granules-map))))))
