(ns cmr.elastic-utils.es-index-helper
  "Defines helper functions for invoking ES index"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
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
