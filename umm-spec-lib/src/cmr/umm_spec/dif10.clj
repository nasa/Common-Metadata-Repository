(ns cmr.umm-spec.dif10
  "Generic DIF 10-related values and functions.")

(def default-date-value "1970-01-01T00:00:00")

(defn latest-date-of-type
  "Returns :Date value of the most recent UMM DateType map in date-coll with the given type."
  [date-coll date-type]
  (->> date-coll
       (filter #(= date-type (:Type %)))
       (map :Date)
       sort
       last))
