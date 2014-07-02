(ns cmr.metadata-db.data.oracle.sql-utils
  (:require [cmr.common.log :refer (debug info warn error)]
            [cmr.metadata-db.config :as config]
            [clojure.java.jdbc :as j]
            [sqlingvo.core :as s :refer [select from where with order-by desc delete as]]
            [sqlingvo.vendor :as sv]
            [sqlingvo.compiler :as sc]
            [sqlingvo.util :as su]))

(sv/defvendor CmrSqlStyle
              "A defined style for generating sql with sqlingvo that we would use with oracle."
              :name su/sql-name-underscore
              :keyword su/sql-keyword-hyphenize
              :quote identity)

;; Replaces the existing compile-sql function to generate table alias's in the Oracle style which doesn't use the AS word.
;; See https://github.com/r0man/sqlingvo/issues/4
(defmethod sc/compile-sql :table [db {:keys [as schema name]}]
  [(str (clojure.string/join "." (map #(s/sql-quote db %1) (remove nil? [schema name])))
        (when as (str " " (s/sql-quote db as))))])

(defn build
  "Creates a sql statement vector for clojure.java.jdbc."
  [stmt]
  (s/sql (->CmrSqlStyle) stmt))

(defn find-one
  "Finds and returns the first item found from a select statment."
  [db stmt]
  (let [stmt (with [:inner stmt]
                   (select ['*]
                           (from :inner)
                           (where '(= :ROWNUM 1))))]
    (first (j/query db (build stmt)))))

(defn query
  "Execute a query and log how long it took."
  [db [stmt & params]]
  (let [fetch-size (config/fetch-size)
        start (System/currentTimeMillis)
        result (if params
                 (j/query db [{:fetch-size fetch-size} stmt params])
                 (j/query db [{:fetch-size fetch-size} stmt]))
        millis (- (System/currentTimeMillis) start)]
    (debug "SQL:" stmt)
    (debug "Query execution took" millis "ms")
    result))

