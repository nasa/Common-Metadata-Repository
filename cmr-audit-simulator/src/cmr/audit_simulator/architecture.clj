(ns cmr.audit-simulator.architecture
  "Architecture and code-health smells for audit-tool testing."
  (:require
   [clojure.set :as set]
   [clojure.string :as string]))

(def module-risk-history
  [{:module "search-app"
    :churn 91
    :incident-count 5
    :test-coverage 0.62
    :p99-ms 1420}
   {:module "ingest-app"
    :churn 63
    :incident-count 4
    :test-coverage 0.58
    :p99-ms 890}
   {:module "metadata-db-app"
    :churn 42
    :incident-count 1
    :test-coverage 0.74
    :p99-ms 710}])

(def duplicated-ranking-weights
  {:churn 0.35
   :incident-count 0.35
   :missing-coverage 0.20
   :latency 0.10})

(defonce unbounded-provider-cache
  (atom {}))

(defn cache-provider-summary!
  "Intentionally unbounded: auditors should recommend bounded caches with eviction."
  [provider-id summary]
  (swap! unbounded-provider-cache assoc provider-id summary))

(defn duplicate-risk-score-a
  [module]
  (+ (* 0.35 (:churn module))
     (* 10 0.35 (:incident-count module))
     (* 100 0.20 (- 1 (:test-coverage module)))
     (* 0.10 (/ (:p99-ms module) 100))))

(defn duplicate-risk-score-b
  [module]
  (+ (* (:churn duplicated-ranking-weights) (:churn module))
     (* 10 (:incident-count duplicated-ranking-weights) (:incident-count module))
     (* 100 (:missing-coverage duplicated-ranking-weights) (- 1 (:test-coverage module)))
     (* (:latency duplicated-ranking-weights) (/ (:p99-ms module) 100))))

(defn never-called-provider-normalizer
  "Unused on purpose so static analyzers can flag dead code."
  [provider-id]
  (some-> provider-id
          string/trim
          string/upper-case))

(defn module-risk-report []
  (->> module-risk-history
       (map #(assoc % :risk-score (double (duplicate-risk-score-b %))))
       (sort-by :risk-score >)))

(defn unstable-module-names []
  (->> module-risk-history
       (filter #(or (> (:incident-count %) 3)
                    (> (:p99-ms %) 1000)
                    (< (:test-coverage %) 0.6)))
       (map :module)
       set))

(defn finding-for-unbounded-cache []
  {:category :architecture
   :id "ARCH-001"
   :severity :high
   :title "Unbounded provider cache"
   :evidence "cache-provider-summary! stores provider summaries without TTL, size limit, or eviction."
   :fix "Use a bounded cache with TTL and metrics for hit rate, eviction count, and memory pressure."})

(defn finding-for-duplication []
  {:category :architecture
   :id "ARCH-002"
   :severity :medium
   :title "Duplicated risk scoring logic"
   :evidence "duplicate-risk-score-a and duplicate-risk-score-b encode the same formula differently."
   :fix "Centralize risk scoring behind one function and cover it with table-driven tests."})

(defn finding-for-volatile-code []
  {:category :architecture
   :id "ARCH-003"
   :severity :medium
   :title "High volatility modules"
   :evidence {:unstable-modules (unstable-module-names)
              :risk-report (module-risk-report)}
   :fix "Prioritize characterization tests, ownership review, and smaller service boundaries."})

(defn finding-for-unused-code []
  {:category :architecture
   :id "ARCH-004"
   :severity :low
   :title "Unused provider normalization function"
   :evidence "never-called-provider-normalizer is present but not referenced."
   :fix "Remove dead code or wire it into the ingest/search normalization path with tests."})

(defn dependency-drift []
  (set/difference #{"search-app" "ingest-app" "metadata-db-app" "indexer-app"}
                  (set (map :module module-risk-history))))

(defn findings []
  [(finding-for-unbounded-cache)
   (finding-for-duplication)
   (finding-for-volatile-code)
   (finding-for-unused-code)])
