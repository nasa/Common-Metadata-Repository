
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


