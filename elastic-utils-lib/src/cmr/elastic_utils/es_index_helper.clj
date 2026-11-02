(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [cmr.elastic-utils.es-util :as es-util]
   [cmr.transmit.config :as config]))

(defn index-alias
  "Returns the default index alias for the given index"
  [index-name]
  (str index-name "_alias"))

(defn exists?
  "Return true if the given index exists"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name)
        response (client/head url (merge (:http-opts conn) {:throw-exceptions false}))]
    (= 200 (:status response))))

(defn written-index-names
  "Returns the names of the given indexes that have had documents indexed into or deleted from them
  since they were created. Indexes that don't exist are left out."
  [conn index-names]
  (let [url (es-util/url-with-path conn (vec index-names) "_stats" "indexing,docs")
        response (client/get url (merge (:http-opts conn)
                                        {:accept :json
                                         :query-params {:ignore_unavailable true}}))]
    (for [[index-name {:keys [primaries]}] (:indices (es-util/decode-response response))
          :when (some pos? [(get-in primaries [:indexing :index_total] 0)
                            (get-in primaries [:indexing :delete_total] 0)
                            (get-in primaries [:docs :count] 0)
                            (get-in primaries [:docs :deleted] 0)])]
      (name index-name))))

(defn metadata-versions
  "Returns the state and the mapping, settings and aliases versions of the given indexes by index
  name. Elasticsearch raises a version whenever that part of the index changes. Indexes that don't
  exist are left out."
  [conn index-names]
  (let [url (es-util/url-with-path conn "_cluster/state/metadata" (vec index-names))
        fields ["state" "mapping_version" "settings_version" "aliases_version"]
        response (client/get url (merge (:http-opts conn)
                                        {:accept :json
                                         :query-params {:ignore_unavailable true
                                                        :filter_path (string/join
                                                                      ","
                                                                      (map #(str "metadata.indices.*." %) fields))}}))]
    (into {}
          (for [[index-name metadata] (get-in (es-util/decode-response response) [:metadata :indices])]
            [(name index-name) metadata]))))

(defn update-mapping
  "Register or modify specific mapping definition. Note that ES index mapping updates performs a MERGE and not a REPLACE. So properties are either added or changed, but never deleted."
  [conn index-name-or-names _type-name opts]
  (let [{:keys [mapping]} opts
        url (es-util/url-with-path conn index-name-or-names "_mapping")
        response (client/put url
                             (merge (:http-opts conn)
                                    {:content-type :json
                                     :body (json/generate-string mapping)
                                     :query-params (dissoc opts :mapping)
                                     :accept :json
                                     :throw-exceptions false}))
        status (:status response)]
    (if (some #{status} [200 201])
      (es-util/decode-response response)
      (throw (ex-info (str "Update mapping failed with status " status)
                      {:status status :body (:body response)})))))

(defn create
  "Create an index"
  [conn index-name opts]
  (let [{:keys [settings mappings]} opts
        url (es-util/url-with-path conn index-name)
        body (cond-> {:settings (or settings {})}
               mappings (assoc :mappings mappings))]
    (let [response (client/put url
                               (merge (:http-opts conn)
                                      {:content-type :json
                                       :body (json/generate-string body)
                                       :query-params (dissoc opts :mappings :settings)
                                       :accept :json
                                       :throw-exceptions false}))
          status (:status response)]
      (if (some #{status} [200 201])
        (es-util/decode-response response)
        (throw (ex-info (str "Create index failed with status " status)
                        {:status status :body (:body response)}))))))

(defn refresh
  "Refresh an index"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name "_refresh")]
    (es-util/decode-response
     (client/post url (merge (:http-opts conn)
                             {:accept :json
                              :content-type :json
                              :headers {:client-id config/cmr-client-id}})))))

(defn delete
  "Delete an index"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name)]
    (es-util/decode-response
     (client/delete url (merge (:http-opts conn)
                               {:accept :json})))))

(defn update-aliases
  "Update index aliases"
  [conn actions]
  (let [url (es-util/url-with-path conn "_aliases")]
    (es-util/decode-response
     (client/post url (merge (:http-opts conn)
                             {:content-type :json
                              :body (json/generate-string {:actions actions})
                              :accept :json})))))

(defn get-aliases
  "Get index aliases"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name "_alias")
        response (client/get url (merge (:http-opts conn)
                                        {:accept :json
                                         :throw-exceptions false}))
        status (:status response)]
    (if (= 404 status)
      []
      (let [resp (es-util/decode-response response)
            aliases (keys (get-in resp [(keyword index-name) :aliases]))]
        (mapv name aliases)))))

(defn alias-exists?
  "Return true if the given index has the default alias in the form of <index-name>_alias"
  [conn index-name]
  (boolean (some #{(index-alias index-name)} (get-aliases conn index-name))))

(defn get-indexes-mapped-to-alias
  "Return list of indexes mapped to the given alias name"
  [conn alias-name]
  (let [url (es-util/url-with-path conn "_alias/" alias-name)
        resp-json (client/get url (merge (:http-opts conn)
                                         {:accept :json
                                          :throw-exceptions false
                                          :as :json}))]
    (if (= 200 (:status resp-json))
      (vec (keys (:body resp-json)))
      nil)))

(defn create-index-template
  "Create an index template in elasticsearch"
  [conn template-name opts]
  (let [{:keys [index-patterns settings mappings aliases]} opts
        url (es-util/url-with-path conn "_index_template" template-name)
        template (merge {:settings settings}
                        (when mappings {:mappings mappings})
                        (when aliases {:aliases aliases}))
        body {:index_patterns index-patterns
              :template template}]
    (es-util/decode-response
     (client/post url (merge (:http-opts conn)
                             {:content-type :json
                              :body (json/generate-string body)
                              :accept :json})))))

(defn get-mapping
  "Get the mapping for an index"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name "_mapping")]
    (es-util/decode-response
     (client/get url (merge (:http-opts conn)
                            {:accept :json
                             :throw-exceptions false})))))

(defn get-settings
  "Get the settings for an index"
  [conn index-name]
  (let [url (es-util/url-with-path conn index-name "_settings")]
    (es-util/decode-response
     (client/get url (merge (:http-opts conn)
                            {:accept :json
                             :throw-exceptions false})))))
