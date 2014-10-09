(ns cmr.search.models.query-zipper
  "Implements the clojure.zip API for queries"
  (:require [cmr.search.models.query :as qm]
            [clojure.zip :as z])
  (:import [cmr.search.models.query
            Query
            ConditionGroup
            NegatedCondition
            CollectionQueryCondition
            NestedCondition
            StringCondition]))


(defprotocol QueryZipperHelper
  "Provides implementations of functions that are different for each condition"
  (branch? [loc])
  (children [loc])
  (make-node [loc node-children]))

(defn query-zipper
  "Constructs a zipper from a query or query condition."
  [query]
  (z/zipper branch? children make-node query))

(defn- assert-single-child
  [node-children]
  (when (not= (count node-children) 1)
    (throw (Exception. (str "Expected a single child node"
                            (pr-str node-children))))))

(extend-protocol QueryZipperHelper
  Query
  (branch? [_] true)

  (children
    [query]
    [(:condition query)])

  (make-node
    [query node-children]
    (assert-single-child node-children)
    (assoc query :condition (first node-children)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ConditionGroup
  (branch? [_] true)

  (children
    [group]
    (:conditions group))

  (make-node
    [group node-children]
    (assoc group :conditions node-children))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  NegatedCondition
  (branch? [_] true)

  (children
    [c]
    (:condition c))

  (make-node
    [c node-children]
    (assert-single-child node-children)
    (assoc c :condition (first node-children)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  CollectionQueryCondition
  (branch? [_] true)

  (children
    [c]
    (:condition c))

  (make-node
    [c node-children]
    (assert-single-child node-children)
    (assoc c :condition (first node-children)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  NestedCondition
  (branch? [_] true)

  (children
    [c]
    (:condition c))

  (make-node
    [c node-children]
    (assert-single-child node-children)
    (assoc c :condition (first node-children)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  Object
  (branch? [_] false)

  (children
    [loc]
    (throw (Exception. "This node does not have children.")))

  (make-node
    [loc node-children]
    (throw (Exception. "This node can not have children."))))

