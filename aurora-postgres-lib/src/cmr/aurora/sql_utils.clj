(ns cmr.aurora.sql-utils
  (:refer-clojure :exclude [update])
  (:require
   [clojure.java.jdbc :as j]
   [cmr.common.log :refer (debug info warn error)]
   [sqlingvo.compiler :as sc]
   [sqlingvo.core :as s]
   [sqlingvo.db :as sdb]
   [sqlingvo.util :as su]))

(def db-vendor (sdb/postgresql))

;; Wrap sqlingvo core functions for uniform access
(def select
  (partial s/select db-vendor))

(def with
  (partial s/with db-vendor))

(def insert
  (partial s/insert db-vendor))

(def delete
  (partial s/delete db-vendor))

(def update
  (partial s/update db-vendor))

(def from
  s/from)

(def where
  s/where)

(def values
  s/values)

(def order-by
  s/order-by)

(def desc
  s/desc)

(def as
  s/as)

(defn build
  "Creates a sql statement vector for clojure.java.jdbc."
  [stmt]
  (s/sql stmt))

(defn run-sql
  "Run the given sql statement in string. Only intended for internal use."
  [db statement]
  (j/db-do-prepared db statement []))

(defn query
  "Execute a query and log how long it took."
  [db stmt-and-params]
  (let [fetch-size 200 ;; for oracle this is assoc'ed into the db object at system start, from a defconfig. workload env does not have an overwriting parameter so hardcoding the default here for prototype runs
        start (System/currentTimeMillis)
        result (j/query db (cons {:fetch-size fetch-size} stmt-and-params))
        millis (- (System/currentTimeMillis) start)]
    ;; We have about 100 per day of these long running queries
    (when (> millis 60000)
      (warn (format "Query execution took [%d] ms, SQL: %s" millis (first stmt-and-params))))
    result))

(defn find-one
  "Finds and returns the first item found from a select statment."
  [db stmt]
  (first (query db (build stmt))))
