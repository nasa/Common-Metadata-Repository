(ns cmr.search.services.query-walkers.query-pretty-printer
  "Changes the way queries and query conditions are pretty printed. Records that are pretty printed
  do not include type information. This pretty prints the queries as a map with a type field
  that contains the object class type. It also prints out the fields in the order they are defined
  in the record."
  (:require [cmr.common.services.errors :as errors]
            [cmr.search.models.query :as qm]
            [camel-snake-kebab :as csk]
            [cmr.common.util :as util]
            [clojure.pprint :as pprint])
  (:import [cmr.search.models.query
            Query
            ConditionGroup
            NestedCondition
            TextCondition
            StringCondition
            StringsCondition
            NegatedCondition
            BooleanCondition
            SpatialCondition
            ScriptCondition
            ExistCondition
            MissingCondition
            DateValueCondition
            DateRangeCondition
            NumericValueCondition
            NumericRangeCondition
            StringRangeCondition
            TemporalCondition
            OrbitNumberValueCondition
            OrbitNumberRangeCondition
            EquatorCrossingLongitudeValueCondition
            EquatorCrossingLongitudeRangeCondition
            EquatorCrossingDateCondition
            CoordinateValueCondition
            CoordinateRangeCondition
            TwoDCoordinateCondition
            TwoDCoordinateSystemCondition
            CollectionQueryCondition
            MatchAllCondition
            MatchNoneCondition
            AttributeNameCondition
            AttributeValueCondition
            AttributeRangeCondition]))

(defprotocol MapifyQuery
  (mapify
    [c]
    "Converts a record into a map with a :type key mapped to a simplfied name of the record class name"))

(defn mapify-record
  "Converts a record into a map with a :type key containing a simple name of the key. The map keys
  are ordered to match the order defined in the record"
  ([r]
   (let [type-name (symbol (.getSimpleName ^Class (type r)))]
     (util/remove-nil-keys (into {} (assoc r :type type-name)))))
  ([fields r]
   (let [type-name (symbol (.getSimpleName ^Class (type r)))
         key-order-map (into {} (map-indexed #(vector %2 %1) (cons :type fields)))
         sorted-by-key-order (sorted-map-by #(compare (key-order-map %1) (key-order-map %2)))]
     (util/remove-nil-keys (into sorted-by-key-order (assoc r :type type-name))))))

(comment

  (def q (qm/query {:concept-type :granule :condition (qm/string-condition :foo "f")}))
  (mapify-record (util/record-fields cmr.search.models.query.Query) q)
  (mapify q)
  (pprint/pprint q)

  )


(def query-types
  [Query
   ConditionGroup
   CollectionQueryCondition
   NegatedCondition
   NestedCondition
   TemporalCondition
   TextCondition
   StringCondition
   StringsCondition
   BooleanCondition
   SpatialCondition
   ScriptCondition
   ExistCondition
   MissingCondition
   DateValueCondition
   DateRangeCondition
   NumericValueCondition
   NumericRangeCondition
   StringRangeCondition
   OrbitNumberValueCondition
   OrbitNumberRangeCondition
   EquatorCrossingLongitudeValueCondition
   EquatorCrossingLongitudeRangeCondition
   EquatorCrossingDateCondition
   CoordinateValueCondition
   CoordinateRangeCondition
   TwoDCoordinateCondition
   TwoDCoordinateSystemCondition
   MatchAllCondition
   MatchNoneCondition
   AttributeNameCondition
   AttributeValueCondition
   AttributeRangeCondition])

(doseq [query-type query-types]
  ;; Allows queries to be pretty printed using the mapify function
  (.addMethod ^clojure.lang.MultiFn clojure.pprint/simple-dispatch
              query-type
              (fn [q]
                (pprint/pprint (mapify q)))))


(extend-protocol MapifyQuery
  Query
  (mapify
    [q]
    (mapify-record (util/record-fields Query)
                   (update-in q [:condition] mapify)))

  ConditionGroup
  (mapify
    [c]
    (mapify-record (util/record-fields ConditionGroup)
                   (update-in c [:conditions] #(map mapify %))))

  CollectionQueryCondition
  (mapify
    [c]
    (mapify-record (util/record-fields CollectionQueryCondition)
                   (update-in c [:condition] mapify)))

  NegatedCondition
  (mapify
    [c]
    (mapify-record (util/record-fields NegatedCondition)
                   (update-in c [:condition] mapify)))

  NestedCondition
  (mapify
    [c]
    (mapify-record (util/record-fields NestedCondition)
                   (update-in c [:condition] mapify)))

  TemporalCondition
  (mapify
    [c]
    (mapify-record (util/record-fields TemporalCondition) c))

  TextCondition
  (mapify
    [c]
    (mapify-record (util/record-fields TextCondition) c))

  StringCondition
  (mapify
    [c]
    (mapify-record (util/record-fields StringCondition) c))

  StringsCondition
  (mapify
    [c]

    (let [c (if (> (count (:values c)) 6)
              (update-in c [:values]
                         (fn [values]
                           [(first values) (second values)
                            (str "... " (- (count values) 2) " values hidden ...")]))
              c)]
      (mapify-record (util/record-fields StringsCondition) c)))

  BooleanCondition
  (mapify
    [c]
    (mapify-record (util/record-fields BooleanCondition) c))

  SpatialCondition
  (mapify
    [c]
    (mapify-record (util/record-fields SpatialCondition) c))

  ScriptCondition
  (mapify
    [c]
    (mapify-record (util/record-fields ScriptCondition) c))

  ExistCondition
  (mapify
    [c]
    (mapify-record (util/record-fields ExistCondition) c))

  MissingCondition
  (mapify
    [c]
    (mapify-record (util/record-fields MissingCondition) c))

  DateValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields DateValueCondition) c))

  DateRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields DateRangeCondition) c))

  NumericValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields NumericValueCondition) c))

  NumericRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields NumericRangeCondition) c))

  StringRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields StringRangeCondition) c))

  OrbitNumberValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields OrbitNumberValueCondition) c))

  OrbitNumberRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields OrbitNumberRangeCondition) c))

  CoordinateValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields CoordinateValueCondition) c))

  CoordinateRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields CoordinateRangeCondition) c))

  TwoDCoordinateCondition
  (mapify
    [c]
    (mapify-record (util/record-fields TwoDCoordinateCondition) c))

  TwoDCoordinateSystemCondition
  (mapify
    [c]
    (mapify-record (util/record-fields TwoDCoordinateSystemCondition) c))

  EquatorCrossingLongitudeValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields EquatorCrossingLongitudeValueCondition) c))

  EquatorCrossingLongitudeRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields EquatorCrossingLongitudeRangeCondition) c))

  EquatorCrossingDateCondition
  (mapify
    [c]
    (mapify-record (util/record-fields EquatorCrossingDateCondition) c))

  MatchAllCondition
  (mapify
    [c]
    (mapify-record (util/record-fields MatchAllCondition) c))

  MatchNoneCondition
  (mapify
    [c]
    (mapify-record (util/record-fields MatchNoneCondition) c))

  AttributeNameCondition
  (mapify
    [c]
    (mapify-record (util/record-fields AttributeNameCondition) c))

  AttributeValueCondition
  (mapify
    [c]
    (mapify-record (util/record-fields AttributeValueCondition) c))

  AttributeRangeCondition
  (mapify
    [c]
    (mapify-record (util/record-fields AttributeRangeCondition) c))

  nil
  (mapify [_] nil))

