(ns cmr.audit-simulator.reliability
  "Reliability and performance pressure examples for audit-tool testing.")

(def endpoint-metrics
  [{:endpoint "/audit-simulator/search/collections"
    :p50-ms 110
    :p95-ms 890
    :p99-ms 1670
    :error-rate 0.012
    :cache-hit-rate 0.44
    :dominant-query-shape "free_text + temporal + bounding_box"}
   {:endpoint "/audit-simulator/search/granules"
    :p50-ms 180
    :p95-ms 1230
    :p99-ms 2410
    :error-rate 0.021
    :cache-hit-rate 0.31
    :dominant-query-shape "collection_concept_id + polygon + sort_key"}
   {:endpoint "/audit-simulator/ingest/providers/AUDIT/collections"
    :p50-ms 95
    :p95-ms 620
    :p99-ms 980
    :error-rate 0.006
    :cache-hit-rate 0.72
    :dominant-query-shape "provider_id + native_id"}])

(def incidents
  [{:id "SIM-INC-001"
    :started-at "2026-07-01T14:00:00Z"
    :duration-minutes 17
    :suspected-cause "Retry storm after metadata-db timeout."}
   {:id "SIM-INC-002"
    :started-at "2026-07-04T09:12:00Z"
    :duration-minutes 9
    :suspected-cause "Search query fan-out created elastic queue pressure."}])

(defn slow-search
  "Intentionally inefficient O(n*m) join-like loop for auditors to flag."
  [collections granules]
  (for [collection collections
        granule granules
        :when (= (:concept-id collection) (:collection-concept-id granule))]
    (assoc granule :collection-short-name (:short-name collection))))

(defn serial-provider-health-check
  "Simulates serial downstream calls that should be parallelized or cached."
  [providers health-client]
  (mapv health-client providers))

(defn endpoint-risk [metric]
  (+ (/ (:p99-ms metric) 1000.0)
     (* 20 (:error-rate metric))
     (* 2 (- 1 (:cache-hit-rate metric)))))

(defn risky-endpoints []
  (->> endpoint-metrics
       (filter #(or (> (:p99-ms %) 1000)
                    (> (:error-rate %) 0.01)
                    (< (:cache-hit-rate %) 0.5)))
       (map #(assoc % :risk-score (endpoint-risk %)))
       (sort-by :risk-score >)))

(defn finding-for-p99 []
  {:category :reliability
   :id "REL-001"
   :severity :high
   :title "Search endpoints exceed one second p99"
   :evidence (risky-endpoints)
   :fix "Profile dominant query shapes, add query-specific caching, and benchmark index/query changes."})

(defn finding-for-retry-storm []
  {:category :reliability
   :id "REL-002"
   :severity :high
   :title "Incident history points to retry amplification"
   :evidence incidents
   :fix "Add bounded retries with jitter, circuit breakers, and per-dependency timeout budgets."})

(defn finding-for-inefficient-code []
  {:category :reliability
   :id "REL-003"
   :severity :medium
   :title "Inefficient in-memory join"
   :evidence "slow-search performs a nested scan across collections and granules."
   :fix "Index collections by concept-id before enriching granules."})

(defn findings []
  [(finding-for-p99)
   (finding-for-retry-storm)
   (finding-for-inefficient-code)])
