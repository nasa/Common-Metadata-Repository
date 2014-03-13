(ns cmr.search.models.query
  "Defines various query models and conditions.")

(defrecord Query
  [
   ;; The concept type that is being queried.
   concept-type

   ;; The root level condition
   condition
   ])

(defrecord ConditionGroup
  [
   ;; The operation combining the conditions i.e. :and or :or
   operation

   ;; A sequence of conditions in the group
   conditions
   ])

(defrecord StringCondition
  [
   ;; The field being searched.
   field

   ;; The value to match
   value

   ;; indicates if the search is case sensitive. Defaults to true.
   case-sensitive?

   ;; Indicates if the search contains pattern matching expressions. Defaults to false.
   pattern?
   ])

(defrecord MatchAllCondition
  [])

(defn query
  "Constructs a query with the given type and root condition.
  If root condition is not provided it matches everything."
  ([type]
   (query type (->MatchAllCondition)))
  ([type condition]
   (->Query type condition)))

;; FIXME write test for this stuff after initial prototyping

(defn and-conds
  "Combines conditions in an AND condition."
  [conditions]
  (if (> (count conditions) 1)
    (->ConditionGroup :and conditions)
    (first conditions)))

(defn or-conds
  "Combines conditions in an OR condition."
  [conditions]
  (if (> (count conditions) 1)
    (->ConditionGroup :or conditions)
    (first conditions)))

