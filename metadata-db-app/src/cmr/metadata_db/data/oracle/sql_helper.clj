(ns cmr.metadata-db.data.oracle.sql-helper
  "Contains helper functions that are shared by providers and concepts."
  (:require
   [clojure.java.jdbc :as j]
   [clojure.string :as string]
   [cmr.common.log :refer [info]]
   [cmr.common.services.errors :as errors]
   [cmr.metadata-db.data.oracle.concept-tables :as ct]
   [cmr.oracle.sql-utils :as su :refer [insert values select from where with order-by desc delete as]])
  (:import cmr.oracle.connection.OracleStore))

(defn find-params->sql-clause
  "Converts a parameter map for finding concept types into a sql clause for inclusion in a query. The type
  of value determines the nature of the clause. If the value for a parameter is sequential then a clause
  using 'in' is generated. If the value is a map then it must contain two keys, :comparator which specifies
  what comparision operation to use, e.g. `> or `<, and :value which specifies the value for comparision.
  Any other value type results in a simple 'equals' clause.

  Examples:
             {:provider-id \"PROV1\"}               =>   `(= :provider-id \"PROV1\")

             {:provider-id [\"PROV1\", \"PROV2\"]}  =>   `(in :provider-id [\"PROV1\" \"PROV2\"])

             {:revision-id {:comparator `>, :value \"2000-01-01T10:00:00Z\"}} =>
                    `(> :revision-id \"2000-01-01T10:00:00Z\")"
  ([params]
   (find-params->sql-clause params false))
  ([params or?]
   ;; Validate parameter names as a sanity check to prevent sql injection
   (let [valid-param-name #"^[a-zA-Z][a-zA-Z0-9_\-]*$"]
     (when-let [invalid-names (seq (filter #(not (re-matches valid-param-name (name %))) (keys params)))]
       (errors/internal-error! (format "Attempting to search with invalid parameter names [%s]"
                                       (string/join ", " invalid-names)))))
   (let [comparisons (for [[k v] params]
                       (cond
                         (sequential? v) (let [val (seq v)]
                                           `(in ~k ~val))
                         (map? v) (let [{:keys [value comparator]} v]
                                    `(~comparator ~k ~value))
                         :else `(= ~k ~v)))]
     (if (> (count comparisons) 1)
       (if or?
         (cons `or comparisons)
         (cons `and comparisons))
       (first comparisons)))))

;; ORIG FUNC
;(defn force-delete-concept-by-params
;  "Delete the concepts based on params. concept-type and provider-id must be one of the params.
;  This function is moved from the concepts namespace to avoid cyclic inclusion issue."
;  [db provider params]
;  (let [{:keys [concept-type]} params
;        params (if (:small provider)
;                 (dissoc params :concept-type)
;                 (dissoc params :concept-type :provider-id))
;        table (ct/get-table-name provider concept-type)
;        sql-query (delete table
;                          (where (find-params->sql-clause params)))
;        ;; TODO we need to batch these deletes instead of one large chunk
;        stmt (su/build sql-query)]
;    (info (format "CMR-11560 - INSIDE force-delete-concept-by-params with provider %s and params %s and and table %s and sql-query %s statement %s"
;                  provider params table sql-query stmt))
;    (j/execute! db stmt)))

(defn force-delete-concept-by-params
  "Delete the concepts based on params in batches to prevent overwhelming db.
  concept-type and provider-id must be one of the params.
  This function is moved from the concepts namespace to avoid cyclic inclusion issue.
  To make sure it properly auto-commits between batches, do not wrap this func in a with-db-transaction func (which will disable auto-commit)"
  [db provider params]
  (let [{:keys [concept-type]} params
        params (if (:small provider)
                 (dissoc params :concept-type)
                 (dissoc params :concept-type :provider-id))
        table (ct/get-table-name provider concept-type)

        ;; Build the base query
        sql-query (delete table
                          (where (find-params->sql-clause params)))
        built-stmt (su/build sql-query)

        ;; Extract the SQL string and the values from the built statement
        sql-str (first built-stmt)
        sql-vals (rest built-stmt)
        batch-size 10000

        ;; Append ROWNUM <= ? to the SQL string, and add batch-size to the values array.
        ;; This transforms ["DELETE FROM t WHERE col = ?" "val"]
        ;; into ["DELETE FROM t WHERE col = ? AND ROWNUM <= ?" "val" 10000]
        stmt (into [(str sql-str " AND ROWNUM <= ?")]
                   (concat sql-vals [batch-size]))]

    (info (format "CMR-11560 - INSIDE force-delete-concept-by-params. Batching query: %s" stmt))

    ;; Loop and execute until 0 rows are deleted
    (loop [total-deleted 0
           attempt 1]
      (let [;; jdbc/execute! returns a sequence containing the update count, e.g., (10000)
            rows-deleted (first (j/execute! db stmt))]
        (if (> rows-deleted 0)
          (do
            ;; Pause for 10ms to let Oracle safely flush the Undo Tablespace to disk
            (Thread/sleep 10)
            (recur (+ total-deleted rows-deleted) (inc attempt)))

          ;; Loop finished!
          (do
            (info (format "CMR-11560 - Finished deleting %d %s(s) in %d batches from table %s."
                          total-deleted concept-type attempt table))
            total-deleted))))))