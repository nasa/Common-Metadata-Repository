(ns cmr.system-int-test.utils.elastic-util
  "Helper to connect and take actions directly against elastic cluster for sys int tests"
  (:require
    [cheshire.core :as json]
    [clj-http.client :as client]
    [clojure.string :as string]
    [cmr.system-int-test.system :as s]
    [cmr.system-int-test.utils.url-helper :as url]))

(defn doc-present?
  "If doc is present return true, otherwise return false"
  [index-name doc-id elastic-name]
  (let [response (client/post
                   (format "%s/%s/_search" (url/elastic-root elastic-name) index-name)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)
                    :body (json/encode {:query {:term {:_id doc-id}}})
                    :content-type :json})
        _ (when-not (= 200 (:status response))
            (throw (ex-info "Elasticsearch document presence check failed"
                            {:status (:status response)
                             :body (:body response)
                             :index-name index-name
                             :doc-id doc-id})))
        body (json/decode (:body response) true)]
    (and (= 1 (get-in body [:hits :total :value]))
         (= doc-id (get-in body [:hits :hits 0 :_id])))))

(defn get-doc
  "Get a specific doc from a specified elastic cluster"
  [index-name doc-id elastic-name]
  (let [response (client/get
                   (format "%s/%s/_doc/%s" (url/elastic-root elastic-name) index-name doc-id)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)})
        body (json/decode (:body response) true)]
    body))

(defn index-exists?
  "Check if index exists in elastic cluster"
  [index-name elastic-name]
  (let [response (client/get
                   (format "%s/%s" (url/elastic-root elastic-name) index-name)
                   {:throw-exceptions false
                    :connection-manager (s/conn-mgr)})]
    (= 200 (:status response))))

(defn delete-index-set
  "Deletes the index set with the given id and its indexes from the elastic cluster directly,
  leaving metadata db alone."
  [index-set-id elastic-name]
  (let [root (url/elastic-root elastic-name)
        opts {:throw-exceptions false
              :connection-manager (s/conn-mgr)}
        index-names (->> (client/get (format "%s/_cat/indices/%s_*" root index-set-id)
                                     (assoc opts :query-params {:format "json" :h "index"}))
                         :body
                         (#(json/decode % true))
                         (map :index))]
    (client/delete (format "%s/%s-index-sets/_doc/%s" root elastic-name index-set-id)
                   (assoc opts :query-params {:refresh true}))
    (when (seq index-names)
      (client/delete (format "%s/%s" root (string/join "," index-names)) opts))))
