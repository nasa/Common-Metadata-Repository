(ns cmr.search.data.complex-to-simple-converters.spatial
  "Contains converters for spatial condition into the simpler executable conditions"
  (:require [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.models.query :as qm]
            [cmr.spatial.mbr :as mbr]
            [cmr.spatial.serialize :as srl]
            [cmr.spatial.derived :as d]
            [clojure.string :as str]))


(defn- shape->script-cond
  [shape]
  (let [ords-info-map (-> (srl/shapes->ords-info-map [shape])
                          (update-in [:ords-info] #(str/join "," %))
                          (update-in [:ords] #(str/join "," %)))]
    (qm/map->ScriptCondition {:name "spatial"
                              :params ords-info-map})))

(defn- br->cond
  [prefix {:keys [west north east south] :as br}]
  (letfn [(add-prefix [field]
                      (->> field name (str prefix "-") keyword))
          (range-cond [field from to]
                      (qm/numeric-range-condition (add-prefix field) from to))
          (bool-cond [field value]
                     (qm/->BooleanCondition (add-prefix field) value))]
    (if (mbr/crosses-antimeridian? br)
      (let [c (range-cond :west -180 west)
            d (range-cond :east -180 east)
            e (range-cond :east east 180)
            f (range-cond :west west 180)
            am-conds (qm/and-conds [(qm/or-conds [c f])
                                    (qm/or-conds [d e])
                                    (bool-cond :crosses-antimeridian true)])
            lon-cond (qm/or-conds [(range-cond :west -180 east)
                                   (range-cond :east west 180)
                                   am-conds])]
        (qm/and-conds [lon-cond
                       (range-cond :north south 90)
                       (range-cond :south -90 north)]))

      (let [north-cond (range-cond :north south 90.0)
            south-cond (range-cond :south -90.0 north)
            west-cond (range-cond :west -180 east)
            east-cond (range-cond :east west 180)
            am-conds (qm/and-conds [(bool-cond :crosses-antimeridian true)
                                    (qm/or-conds [west-cond east-cond])])
            non-am-conds (qm/and-conds [west-cond east-cond])]
        (qm/and-conds [north-cond
                       south-cond
                       (qm/or-conds [am-conds non-am-conds])])))))


(extend-protocol c2s/ComplexQueryToSimple
  cmr.search.models.query.SpatialCondition
  (c2s/reduce-query
    [context {:keys [shape]}]
    (let [shape (d/calculate-derived shape)
          mbr-cond (br->cond "mbr" (srl/shape->mbr shape))
          lr-cond (br->cond "lr" (srl/shape->lr shape))
          spatial-script (shape->script-cond shape)]
      (qm/and-conds [mbr-cond (qm/or-conds [lr-cond spatial-script])]))))