(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [clj-http.client :as client]
   [clojurewerkz.elastisch.rest :as rest]
   [clojurewerkz.elastisch.rest.index :as esi]
   [clojurewerkz.elastisch.rest.utils :refer [join-names]]
   [cmr.transmit.config :as config])
  #_{:clj-kondo/ignore [:unused-import]}
  (:import clojurewerkz.elastisch.rest.Connection))

(defn exists?
  [conn index-name]
  (esi/exists? conn index-name))

(defn update-mapping
  "Register or modify specific mapping definition"
  [conn index-name-or-names _type-name opts]
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
 "Refresh an index"
  [conn index-name]
  (-> (rest/index-refresh-url conn (join-names index-name))
      (client/post (merge (.http-opts conn)
                        nil
                        {:accept :json
                         :content-type :json
                         :headers {:client-id config/cmr-client-id}}))
      (:body)
      (rest/parse-safely)))

(defn delete
  "Delete an index"
  [conn index-name]
  (esi/delete conn index-name))

(defn update-aliases
  "Update index aliases"
  [conn actions]
  (rest/post conn
             (rest/index-aliases-batch-url conn)
             {:content-type :json
              :body {:actions actions}}))

;; We have to roll our own get-aliases function because Elasticsearch route on GET alias
;; for an index has changed and clojurewerkz is outdated
(defn get-aliases
  "Get index aliases"
  [conn index-name]
  (let [aliases-url (rest/url-with-path conn index-name "_alias")
        resp (rest/get conn aliases-url)
        aliases (keys (get-in resp [(keyword index-name) :aliases]))]
    (mapv name aliases)))

(defn alias-exists?
  "Return true if the given index has the default alias in the form of <index-name>_alias"
  [conn index-name]
  (boolean (some #{(str index-name "_alias")} (get-aliases conn index-name))))

(defn create-index-template
  "Create an index template in elasticsearch"
  [conn template-name opts]
  (let [{:keys [index-patterns settings mappings aliases]} opts
        template-url (rest/url-with-path conn "_index_template" template-name)
        template (merge {:settings settings}
                        (when mappings {:mappings mappings})
                        (when aliases {:aliases aliases}))
        body (merge {:index_patterns index-patterns
                     :template template})]
    (rest/post conn template-url
               {:content-type :json
                :body body})))
