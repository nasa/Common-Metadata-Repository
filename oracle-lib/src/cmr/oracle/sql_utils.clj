(ns cmr.oracle.sql-utils
  (:refer-clojure :exclude [update])
  (:require [cmr.common.log :refer (debug info warn error)]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as s]
            [sqlingvo.db :as sdb]
            [sqlingvo.compiler :as sc]
            [sqlingvo.util :as su]))

(def db-vendor (sdb/oracle))

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

(defn query
  "Execute a query and log how long it took."
  [db stmt-and-params]
  ;; Uncomment to debug sql
  (debug "SQL:" (first stmt-and-params))

  (let [fetch-size (:result-set-fetch-size db)
        start (System/currentTimeMillis)
        result (j/query db (cons {:fetch-size fetch-size} stmt-and-params))
        millis (- (System/currentTimeMillis) start)]
    (when (> millis 100)
      (debug (format "Query execution took [%d] ms" millis)))
    result))

(defn find-one
  "Finds and returns the first item found from a select statment."
  [db stmt]
  (let [stmt (with [:inner stmt]
                   (select ['*]
                           (from :inner)
                           (where '(= :ROWNUM 1))))]
    (first (query db (build stmt)))))

(defmacro ignore-already-exists-errors
  "Used to make SQL calls where an error indicating that an object already exists can be safely
  ignored."
  [object-name & body]
  `(try
     (do
       ~@body)
     (catch Exception e#
       (if (re-find #"(ORA-00955|ORA-01920):" (.getMessage e#))
         (info (str ~object-name " already exists, ignoring error."))
         (throw e#)))))
