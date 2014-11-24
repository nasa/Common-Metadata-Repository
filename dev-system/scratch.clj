
(do
  (use 'cheshire.core)
  (require '[clojure.set :as set]))

;; Count of granules
(->> (decode (slurp "partnertest_holdings.json") true)
     (map #(update-in % [:granule_count] (fn [v] (Long. v))))
     (map :granule_count)
     (reduce +))

;; Count by provider
; (->> (decode (slurp "/Users/jgilman/work/cmr_workspace/cmr-dev-system/holdings.json") true)
;      (map #(update-in % [:granule_count] (fn [v] (Long. v))))
;      (group-by :provider_id)
;      (#(into {} (for [[k v] %]
;                   [k (reduce + (map :granule_count v))])))
;      (sort-by second)
;      reverse)


(->> (decode (slurp "partnertest_holdings.json") true)
                    (map #(update-in % [:granule_count] (fn [v] (Long. v))))
                    (sort-by :granule_count)
                    reverse
                    (take 10))

(defn holdings->nested-map
  [holdings]
  (reduce (fn [pic {:keys [provider-id granule-count concept-id]}]
            (assoc-in pic [provider-id concept-id] granule-count))
          {}
          holdings))

(defn holdings-map->total
  [holdings-map]
  (reduce + (for [[_ concept-id-map] holdings-map
                  [_ num-granules] concept-id-map]
              num-granules)))

(def testbed-holdings
  (->> (decode (slurp "testbed_holdings.json") true)
       (map #(update-in % [:granule_count] (fn [v] (Long. v))))
       (map (fn [{:keys [dataset_id echo_collection_id granule_count provider_id]}]
              {:granule-count granule_count
               :provider-id provider_id
               :entry-title dataset_id
               :concept-id echo_collection_id}))
       holdings->nested-map))

(def ptest-holdings
  (->> (decode (slurp "ptest_holdings.json") true)
       (map #(update-in % [:granule_count] (fn [v] (Long. v))))
       (map (fn [{:keys [dataset_id echo_collection_id granule_count provider_id]}]
              {:granule-count granule_count
               :provider-id provider_id
               :entry-title dataset_id
               :concept-id echo_collection_id}))
       holdings->nested-map))


(def sit-holdings
  (holdings->nested-map (decode (slurp "sit_holdings.json") true)))

(def uat-holdings
  (holdings->nested-map (decode (slurp "uat_mdb_holdings.json") true)))

(holdings-map->total sit-holdings)
(holdings-map->total testbed-holdings)

(let [[sit-extra tb-extra _] (clojure.data/diff sit-holdings testbed-holdings)]
  {:sit-extra sit-extra
   :tb-extra tb-extra})

(let [[uat-extra ptest-extra _] (clojure.data/diff uat-holdings ptest-holdings)]
  {:uat-extra uat-extra
   :ptest-extra ptest-extra})

(get-in sit-holdings ["EDF_OPS" "C1000000261-EDF_OPS"])
(get-in testbed-holdings ["EDF_OPS" "C1000000261-EDF_OPS"])

(get-in sit-holdings ["EDF_OPS" "C1000000104-EDF_OPS"])
(get-in testbed-holdings ["EDF_OPS" "C1000000104-EDF_OPS"])



