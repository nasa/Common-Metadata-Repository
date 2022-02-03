(ns cmr.elastic-utils.es-helper
  "Defines helper functions for invoking ES"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as string]
   [clojurewerkz.elastisch.rest :as rest]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.response :refer [not-found? hits-from]]
   [clojurewerkz.elastisch.rest.utils :refer [join-names]]))

(defn search
  "Performs a search query across one or more indexes and one or more mapping types"
  [conn index mapping-type opts]
  (let [qk [:search_type :scroll :routing :preference :ignore_unavailable]
        qp (merge {:track_total_hits true}
                  (select-keys opts qk))
        body (apply dissoc (concat [opts] qk))]
    (rest/post conn (rest/search-url conn (join-names index))
               {:content-type :json
                :body body
                :query-params qp})))

(defn count-query
  "Performs a count query over one or more indexes and types"
  [conn index mapping-type query]
  (doc/count conn index mapping-type query))

(defn scroll
  "Performs a scroll query, fetching the next page of results from a query given a scroll id"
  [conn scroll-id opts]
  (doc/scroll conn scroll-id opts))

(defn doc-get
  "Fetches and returns a document by id or `nil` if it does not exist."
  ([conn index mapping-type id]
   (doc-get conn index mapping-type id nil))
  ([conn index mapping-type id opts]
   (let [result (if (empty? opts)
                  (rest/get conn (rest/record-url conn index "_doc" id))
                  (rest/get conn (rest/record-url conn index "_doc" id) {:query-params opts}))]
     (if (not-found? result)
       nil
       result))))

(defn put
  "Creates or updates a document in the search index, using the provided document id"
  ([conn index mapping-type id document]
   (put conn index mapping-type id document nil))
  ([conn index mapping-type id document opts]
   (rest/put conn (rest/record-url conn index "_doc" id)
             {:content-type :json
              :body document
              :query-params opts})))

(defn delete
  "Deletes document from the index."
  ([conn index mapping-type id]
   (delete conn index mapping-type id nil))
  ([conn index mapping-type id opts]
   (-> (rest/record-url conn index "_doc" id)
       (http/delete (merge {:throw-exceptions false}
                           (.http-opts conn)
                           {:content-type :json :query-params opts}
                           {:accept :json}))
       (:body)
       (rest/parse-safely))))

(defn delete-by-query
  "Performs a delete-by-query operation over one or more indexes and types.
  Multiple indexes and types can be specified by passing in a seq of strings,
  otherwise specifying a string suffices."
  ([conn index mapping-type query]
   (delete-by-query conn index mapping-type query nil))
  ([conn index mapping-type query opts]
   (rest/post conn
              (rest/delete-by-query-url
               conn
               (join-names index))
              {:query-params (select-keys opts
                                          (conj doc/optional-delete-query-parameters
                                                :ignore_unavailable))
               :body {:query query}})))

(defn ^:private bulk-with-url
  "Construct the bulk URL and form body to specification provided by:
  https://www.elastic.co/guide/en/elasticsearch/reference/current/docs-bulk.html
  and perform post."
  ([conn url operations]
   (bulk-with-url conn url operations nil))
  ([conn url operations opts]
   (let [bulk-json (map json/encode operations)
         bulk-json (-> bulk-json
                       (interleave (repeat "\n"))
                       (string/join))]
     (rest/post-string conn url
                       {:body bulk-json
                        :content-type "application/x-ndjson"
                        :query-params opts}))))

(defn bulk
  "Performs a bulk operation"
  ([conn operations] (bulk conn operations nil))
  ([conn operations params]
   (when (not-empty operations)
     (bulk-with-url conn (rest/bulk-url conn) operations params))))

(defn clear-scroll
  "Performs a clear scroll call for the given scroll id"
  [conn scroll-id]
  (rest/delete conn (rest/scroll-url conn) {:content-type :json
                                            :body {:scroll_id scroll-id}}))
