(ns cmr.metadata.proxy.concepts.variable
  (:require
   [clojure.string :as string]
   [cmr.exchange.common.results.core :as results]
   [cmr.exchange.common.results.errors :as errors]
   [cmr.http.kit.request :as request]
   [cmr.http.kit.response :as response]
   [cmr.metadata.proxy.results.errors :as metadata-errors]
   [ring.util.codec :as codec]
   [taoensso.timbre :as log]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; XXX We can pull this from configuration once async-get-metadata function
;;     signatures get updated to accept the system data structure as an arg.
(def variables-api-path "/variables")
(def pinned-variable-schema-version "1.4")
(def results-content-type "application/vnd.nasa.cmr.umm_results+json")
(def charset "charset=utf-8")
(def accept-format "%s; version=%s; %s")
(def accept-header (format accept-format
                           results-content-type
                           pinned-variable-schema-version
                           charset))
(def id-search-constraint "concept_id[]")
(def alias-search-constraint "alias[]")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Support/Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Core Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn build-query
  "Build variable search query based on variable-info, which could be
  list of concept-ids, or aliases. search-constraint could be either
  concept_id[] or alias[]." 
  [variable-info search-constraint]
  (string/join
    "&"
    (conj
      (map #(str (codec/url-encode search-constraint) "=" %) variable-info)
      (str "page_size=" (count variable-info))
      (str (codec/url-encode "options[alias][pattern]") "=true"))))

(defn async-get-metadata
  "Given variable-info, which could be a list of concept-ids or aliases, 
  and the search-constraint, which could be concept_id[] or alias[], 
  returns the metadata for the related variables."
  [search-endpoint user-token variable-info search-constraint]
  (if (seq variable-info)
    (let [url (str search-endpoint variables-api-path)
          payload (build-query variable-info search-constraint)]
      (log/debug "Variables query CMR URL:" url)
      (log/debug "Variables query CMR payload:" payload)
      (request/async-post
       url
       (-> {}
           (request/add-token-header user-token)
           (request/add-accept accept-header)
           (request/add-form-ct)
           (request/add-payload payload)
           ((fn [x] (log/trace "Full request:" x) x)))
       {}
       response/json-handler))
    (deliver (promise) [])))

(defn async-get-metadata-by-ids
  "Get variables by ids"
  [search-endpoint user-token ids]
  (async-get-metadata search-endpoint user-token ids id-search-constraint))

(defn async-get-metadata-by-aliases
  "Get variables by aliases"
  [search-endpoint user-token aliases]
  ;; aliases could be variable aliases or group nodes. If it's alias, we will
  ;; search for alias=<alias>, if it's group nodes, we will search for alias=<alias>/* 
  ;; There is no way to distinguish a true alias and a group node. So we will search for both.
  ;; Note: we don't want to search for alias=<alias>* because say <alias>=/a/b/c and it's a group
  ;; node, alias=/a/b/c* will include /a/b/c1/* /a/b/c2/* but we are only interested in /a/b/c/*
  (let [group-nodes (map #(if (= "/" (last (string/split % #""))) 
                            (str % "*")
                            (str % "/*")) aliases)
        aliases-and-nodes (concat aliases group-nodes)] 
    (async-get-metadata search-endpoint user-token aliases-and-nodes alias-search-constraint)))

(defn extract-metadata
  [promise]
  (let [rslts @promise]
    (if (errors/erred? rslts)
      (do
        (log/error metadata-errors/variable-metadata)
        (log/error rslts)
        rslts)
      (do
        (log/trace "Got results from CMR variable search:"
                   (results/elided rslts))
        (log/trace "Remaining results:" (results/remaining-items rslts))
        (:items rslts)))))

(defn get-metadata
  [search-endpoint user-token params]
  (let [promise-by-ids (when-let [ids (:variables params)]
                         (async-get-metadata-by-ids search-endpoint user-token ids))
        promise-by-aliases (when-let [aliases (:variable-aliases params)]
                             (async-get-metadata-by-aliases search-endpoint user-token aliases))
        variables-by-ids (when promise-by-ids 
                           (extract-metadata promise-by-ids))
        variables-by-aliases (when promise-by-aliases
                               (extract-metadata promise-by-aliases))]
    (distinct (concat variables-by-ids variables-by-aliases))))
