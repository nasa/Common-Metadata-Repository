(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojurewerkz.elastisch.rest :as rest]
   [clojurewerkz.elastisch.rest.index :as esi]
   [clojurewerkz.elastisch.rest.utils :refer [join-names]])
  (:import clojurewerkz.elastisch.rest.Connection))

(defn exists?
  [conn index-name]
  (esi/exists? conn index-name))

(defn update-mapping
  "Register or modify specific mapping definition"
  [conn index-name-or-names type-name opts]
  (let [{:keys [mapping]} opts]
    (rest/put conn
              (rest/index-mapping-url conn (join-names index-name-or-names))
              {:content-type :json
               :body mapping
               :query-params (dissoc opts :mapping)
               :throw-exceptions true})))

(defn create
  "Create an index"
  [conn index-name opts]
  (let [{:keys [settings mappings]} opts]
    (rest/put conn
              (rest/index-url conn index-name)
              {:content-type :json
               :body (cond-> {:settings (or settings {})}
                             mappings (assoc :mappings mappings))
               :throw-exceptions true})))

(defn refresh
 "refresh an index"
  [conn index-name]
  (-> (rest/index-refresh-url conn (join-names index-name))
      (http/post (merge (.http-opts conn)
                        nil
                        {:accept :json
                         :content-type :json}))
      (:body)
      (rest/parse-safely)))

(defn delete
  "delete an index"
  [conn index-name]
  (esi/delete conn index-name))

(defn update-aliases
  "update index aliases"
  [conn actions]
  (rest/post conn
             (rest/index-aliases-batch-url conn)
             {:content-type :json
              :body {:actions actions}}))
