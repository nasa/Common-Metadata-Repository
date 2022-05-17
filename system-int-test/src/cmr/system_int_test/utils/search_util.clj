(ns cmr.system-int-test.utils.search-util
  "provides search related utilities."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clj-time.coerce :as tc]
   [clj-time.core :as t]
   [clojure.data.xml :as x]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [clojure.walk]
   [cmr.common-app.api.routes :as routes]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.concepts :as cs]
   [cmr.common.mime-types :as mime-types]
   [cmr.common.test.time-util :as tu]
   [cmr.common.time-keeper :as tk]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.spatial.point :as p]
   [cmr.spatial.points-validation-helpers :as pv]
   [cmr.system-int-test.data2.aql :as aql]
   [cmr.system-int-test.data2.aql-additional-attribute]
   [cmr.system-int-test.data2.atom :as da]
   [cmr.system-int-test.data2.atom-json :as dj]
   [cmr.system-int-test.data2.facets :as f]
   [cmr.system-int-test.data2.kml :as dk]
   [cmr.system-int-test.data2.opendata :as od]
   [cmr.system-int-test.data2.provider-holdings :as ph]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.dev-system-util :as dev-util]
   [cmr.system-int-test.utils.fast-xml :as fx]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm.dif.dif-collection]
   [cmr.umm.iso-mends.iso-mends-collection]
   [cmr.umm.iso-smap.iso-smap-collection]
   [ring.util.codec :as codec]))

(defn enable-writes
  "Enables writes for tags / tag associations."
  [options]
  (let [response (client/post (url/enable-search-writes-url) options)]
    (is (= 200 (:status response)))))

(defn disable-writes
  "Disables writes for tags / tag associations."
  [options]
  (let [response (client/post (url/disable-search-writes-url) options)]
    (is (= 200 (:status response)))))

(defn refresh-collection-metadata-cache
  "Triggers a full refresh of the collection metadata cache in the search application."
  []
  (let [response (client/post
                  (url/refresh-collection-metadata-cache-url)
                  {:connection-manager (s/conn-mgr)
                   :headers {transmit-config/token-header (transmit-config/echo-system-token)}
                   :throw-exceptions false})]
    (is (= 200 (:status response)) (:body response))))

(defn collection-metadata-cache-state
  "Fetches the state of the collection metadata cache"
  []
  (dev-util/eval-in-dev-sys
   `(cmr.search.data.metadata-retrieval.metadata-cache/cache-state
     {:system (deref cmr.search.system/system-holder)})))

(defn csv-response->granule-urs
  "Parses the csv response and returns the first column which is the granule ur."
  [csv-response]
  (->> (str/split (:body csv-response) #"\n")
       (drop 1)
       (map #(str/split % #","))
       (map first)))

(defn csv->tuples
  "Convert a comma-separated-value string into a set of tuples to be use with find-refs."
  ([csv]
   (csv->tuples nil csv))
  ([index csv]
   (let [attribute (if index (format "attribute[%s]" index) "attribute[]")
         [type name min-value max-value] (str/split csv #"," -1)
         tuples [[(str attribute "[name]") name]
                 [(str attribute "[type]") type]]]
     (cond
       (and (not (empty? max-value)) (not (empty? min-value)))
       (into tuples [[(str attribute "[minValue]") min-value]
                     [(str attribute "[maxValue]") max-value]])
       (not (empty? max-value))
       (conj tuples [(str attribute "[maxValue]") max-value])

       max-value ;; max-value is empty but not nil
       (conj tuples [(str attribute "[minValue]") min-value])

       :else ; min-value is really value
       (conj tuples [(str attribute "[value]") min-value])))))

(defn params->snake_case
  "Converts search parameters to snake_case"
  [params]
  (->> params
       (util/map-keys
        (fn [k]
          (let [k (if (keyword? k) (name k) k)]
            (-> k
                csk/->snake_case
                (str/replace "_[" "[")
                (str/replace "_]" "]")))))
       clojure.walk/keywordize-keys))

(deftest params->snake_case-test
  (is (= {(keyword "archive_center[]") ["SEDAC AC" "Larc" "Sedac AC"],
          (keyword "options[archive_center][and]") "false"
          :foo_bar "chew"}
         (params->snake_case
          {"archive-center[]" ["SEDAC AC" "Larc" "Sedac AC"],
           "options[archive-center][and]" "false"
           :foo-bar "chew"}))))

(defmacro get-search-failure-data
  "Executes a search and returns error data that was caught.
  Tests should verify the results this returns."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (let [{status# :status body# :body} (ex-data e#)
             errors# (try
                       (:errors (json/decode body# true))
                       (catch Exception e2#
                         body#))]
         {:status status# :errors errors#}))))

(defn safe-parse-error-xml
  [xml]
  (try
    (cx/strings-at-path (fx/parse-str xml) [:error])
    (catch Exception e
      (.printStackTrace e)
      [xml])))

(defmacro get-search-failure-xml-data
  "Executes a search and returns error data that was caught, parsing the body as an xml string.
  Tests should verify the results this returns."
  [& body]
  `(try
     ~@body
     (catch clojure.lang.ExceptionInfo e#
       (let [{status# :status body# :body} (ex-data e#)
             errors# (safe-parse-error-xml body#)]
         {:status status# :errors errors#}))))

(defn make-raw-search-query
  "Make a query to search with the given query string."
  [concept-type query]
  (let [url (url/search-url concept-type)]
    (get-search-failure-data
     (client/get (str url query) {:connection-manager (s/conn-mgr)}))))

(defn process-response
  "Returns the response body with response status added."
  [{:keys [status body]}]
  (let [body (util/kebab-case-data body)]
    (if (map? body)
      (assoc body :status status)
      {:status status
       :body body})))

(defn retrieve-data-json
  "Returns the data.json response."
  ([]
   (retrieve-data-json {}))
  ([options]
   (let [url (url/data-json-url)
         args (merge {:throw-exceptions false
                      :connection-manager (s/conn-mgr)}
                     options)
         response (client/get url args)
         {:keys [status body]} response]
     (if (= 200 status)
       {:status status
        :results (json/decode body true)}
       response))))

(defn retrieve-concept
  "Returns the concept metadata through the search concept retrieval endpoint using the cmr
  concept-id and optionally revision-id."
  ([concept-id] (retrieve-concept concept-id nil {}))
  ([concept-id revision-id] (retrieve-concept concept-id revision-id {}))
  ([concept-id revision-id options]
   (let [url-extension (get options :url-extension)
         concept-type (cs/concept-prefix->concept-type (if (= (subs concept-id 0 3) "SUB")
                                                         "SUB"
                                                         (subs concept-id 0 1)))
         format-mime-type (or (:accept options) mime-types/echo10)
         url (url/retrieve-concept-url concept-type concept-id revision-id)
         url (if url-extension
               (str url "." url-extension)
               url)
         args (merge {:accept (when-not url-extension format-mime-type)
                      :throw-exceptions false
                      :connection-manager (s/conn-mgr)}
                     options)]
     (client/get url args))))

(defn find-concepts-in-format
  "Returns the concepts in the format given."
  ([format concept-type params]
   (find-concepts-in-format format concept-type params {}))
  ([format concept-type params options]
   ;; no-snake-kebab needed for legacy psa which use camel case minValue/maxValue
   (let [url-extension (get options :url-extension)
         snake-kebab? (get options :snake-kebab? true)
         throw-exceptions? (get options :throw-exceptions true)
         headers (get options :headers {})
         params (if snake-kebab?
                  (params->snake_case (util/map-keys csk/->snake_case_keyword params))
                  params)
         [url accept] (if url-extension
                        [(str (url/search-url concept-type) "." url-extension)]
                        [(url/search-url concept-type) (or (:accept options) format)])
         request-map {:url url
                      :method (get options :method :get)
                      :accept accept
                      :headers headers
                      :throw-exceptions throw-exceptions?
                      :connection-manager (s/conn-mgr)}
         request-map (if (= :post (:method request-map))
                       (assoc request-map
                              :form-params params
                              :content-type :x-www-form-urlencoded)
                       (assoc request-map :query-params params))
         response (client/request request-map)]
     (when throw-exceptions?
       (is (= 200 (:status response))))
     response)))

(defn- find-with-json-query
  "Executes a search using a JSON query request and returns the results."
  ([concept-type query-params json-as-map]
   (find-with-json-query concept-type query-params json-as-map mime-types/xml))
  ([concept-type query-params json-as-map result-format]
   (find-with-json-query concept-type query-params json-as-map result-format {}))
  ([concept-type query-params json-as-map result-format options]
   (let [headers (get options :headers {})]
     (client/post (url/search-url concept-type)
                  {:accept result-format
                   :content-type mime-types/json
                   :headers headers
                   :body (json/generate-string {:condition json-as-map})
                   :query-params query-params
                   :throw-exceptions true
                   :connection-manager (s/conn-mgr)}))))

(defn get-autocomplete-suggestions
  "Executes a query to the autocomplete endpoint with the given value and returns the results."
  ([query]
   (get-autocomplete-suggestions query nil))
  ([query opts]
   (client/get (url/autocomplete-url query) (merge opts {:throw-exceptions false}))))

(defn get-autocomplete-json
  "Execute autocomplete query and parse json response"
  ([query]
   (json/decode (:body (get-autocomplete-suggestions query)) true))
  ([query opts]
   (json/decode (:body (get-autocomplete-suggestions query opts)) true)))

(defn- parse-timeline-interval
  "Parses the timeline response interval component into a more readable and comparable format."
  [[start end num-grans]]
  [(-> start (* 1000) tc/from-long str)
   (-> end (* 1000) tc/from-long str)
   num-grans])

(defn- parse-timeline-response
  "Parses the timeline response into a more readable and comparable format."
  [response]
  (mapv (fn [{:keys [concept-id intervals]}]
          {:concept-id concept-id
           :intervals (mapv parse-timeline-interval intervals)})
        (json/decode response true)))

(defn get-granule-timeline
  "Requests search response as a granule timeline. Parses the granule timeline response."
  ([params]
   (get-granule-timeline params {:method :get}))
  ([params options]
   (let [url-extension (get options :url-extension)
         snake-kebab? (get options :snake-kebab? true)
         headers (get options :headers {})
         include-headers? (get options :include-headers)
         params (if snake-kebab?
                  (params->snake_case (util/map-keys csk/->snake_case_keyword params))
                  params)
         ;; allow interval to be specified as a keyword
         params (update-in params [:interval] #(if % (name %) ""))
         [url accept] (if url-extension
                        [(str (url/timeline-url) "." url-extension)]
                        [(url/timeline-url) mime-types/json])
         get-request? (= :get (:method options))
         response (get-search-failure-data
                   (if get-request?
                     (client/get url {:accept accept
                                      :headers headers
                                      :query-params params
                                      :connection-manager (s/conn-mgr)})
                     (client/post url
                                  {:accept accept
                                   :headers headers
                                   :content-type mime-types/form-url-encoded
                                   :body (codec/form-encode params)
                                   :connection-manager (s/conn-mgr)})))]
     (if (= 200 (:status response))
       (if include-headers?
         (assoc response :results (parse-timeline-response (:body response)))
         {:status (:status response)
          :results (parse-timeline-response (:body response))})
       response))))

(defn get-granule-timeline-with-post
  "Requests search response as a granule timeline through POST. Parses the granule timeline response."
  [params]
  (get-granule-timeline params {:method :post}))

(defn find-concepts-csv
  "Returns the response of granule search in csv format"
  ([concept-type params]
   (find-concepts-csv concept-type params {}))
  ([concept-type params options]
   (get-search-failure-data
    (find-concepts-in-format "text/csv" concept-type params options))))

(defn find-concepts-atom
  "Returns the response of a search in atom format"
  ([concept-type params]
   (find-concepts-atom concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-xml-data
                   (find-concepts-in-format mime-types/atom concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (da/parse-atom-result concept-type body)}
       response))))

(defn find-concepts-in-atom-with-json-query
  "Returns the response of a search using JSON query in ATOM format"
  [concept-type query-params json-as-map]
  (let [response (get-search-failure-data
                  (find-with-json-query concept-type query-params json-as-map mime-types/atom))
        {:keys [status body]} response]
    (if (= status 200)
      {:status status
       :results (da/parse-atom-result concept-type body)}
      response)))

(defn find-concepts-json
  "Returns the response of a search in json format"
  ([concept-type params]
   (find-concepts-json concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                   (find-concepts-in-format mime-types/json concept-type params options))
         {:keys [status headers body]} response
         {:keys [echo-compatible include-facets]} params]
     (if (and echo-compatible include-facets)
       (dj/parse-echo-json-result body)
       (if (= status 200)
         {:status status
          :hits (Long/parseLong (get headers "CMR-Hits"))
          :results (dj/parse-json-result concept-type body)}
         response)))))

(defn find-concepts-in-json-with-json-query
  "Returns the response of a search using JSON query in JSON format"
  [concept-type query-params json-as-map]
  (let [response (get-search-failure-data
                  (find-with-json-query concept-type query-params json-as-map mime-types/json))
        {:keys [status body]} response
        {:keys [echo-compatible include-facets]} query-params]
    (if (and echo-compatible include-facets)
      (dj/parse-echo-json-result body)
      (if (= status 200)
        {:status status
         :results (dj/parse-json-result concept-type body)}
        response))))

(defn find-concepts-kml
  "Returns the response of search in KML format"
  ([concept-type params]
   (find-concepts-kml concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-xml-data
                   (find-concepts-in-format mime-types/kml
                                            concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (dk/parse-kml-results body)}
       response))))

(defn find-concepts-opendata
  "Returns the response of search in opendata format"
  ([concept-type params]
   (find-concepts-opendata concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                   (find-concepts-in-format mime-types/opendata
                                            concept-type params options))
         {:keys [status body]} response]
     (if (= status 200)
       {:status status
        :results (od/parse-opendata-result concept-type body)}
       response))))

(defn- find-concepts-umm-json-common
  "Returns the response of a search in umm-json-format."
  [concept-type params umm-json-format options]
  (let [response (get-search-failure-data
                  (find-concepts-in-format umm-json-format concept-type params options))
        {:keys [status body]} response]
    (if (= status 200)
      {:status status
       :body body
       :scroll-id (get-in response [:headers routes/SCROLL_ID_HEADER])
       :search-after (get-in response [:headers routes/SEARCH_AFTER_HEADER])
       :content-type (get-in response [:headers "content-type"])
       :results (json/decode body true)}
      response)))

(defn find-concepts-umm-json
  "Returns the response of a search in umm-json format."
  ([concept-type params]
   (find-concepts-umm-json concept-type params {}))
  ([concept-type params options]
   (find-concepts-umm-json-common concept-type params mime-types/umm-json options)))

(defn find-concepts-legacy-umm-json
  "Returns the response of a search in legacy-umm-json format."
  ([concept-type params]
   (find-concepts-legacy-umm-json concept-type params {}))
  ([concept-type params options]
   (find-concepts-umm-json-common concept-type params mime-types/legacy-umm-json options)))

(defn find-concepts-stac
  "Returns the response of a search in stac format"
  ([concept-type params]
   (find-concepts-stac concept-type params {}))
  ([concept-type params options]
   (let [response (get-search-failure-data
                   (find-concepts-in-format mime-types/stac concept-type params options))
         {:keys [status headers body]} response]
     (if (= status 200)
         {:status status
          :headers headers
          :results (json/decode body true)}
         response))))

(defn find-metadata
  "Returns the response of concept search in a specific metadata XML format."
  ([concept-type format-key params]
   (find-metadata concept-type format-key params {}))
  ([concept-type format-key params options]
   (get-search-failure-xml-data
    (let [format-mime-type (mime-types/format->mime-type format-key)
          response (find-concepts-in-format format-mime-type concept-type params options)
          scroll-id (get-in response [:headers routes/SCROLL_ID_HEADER])
          search-after (get-in response [:headers routes/SEARCH_AFTER_HEADER])
          body (:body response)
          parsed (fx/parse-str body)
          hits (cx/long-at-path parsed [:hits])
          metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                      (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))
          items (map (fn [result metadata]
                       (let [{{:keys [concept-id collection-concept-id revision-id granule-count format
                                      has-granules echo_dataset_id echo_granule_id]} :attrs} result
                              ;; For echo compatible result, there is no format attribute on the result.
                              ;; So we simply set the format to the input format-key.
                             metadata-format (if (:echo-compatible params)
                                               format-key
                                               (mime-types/mime-type->format format))]
                         (util/remove-nil-keys
                          {:concept-id concept-id
                           :revision-id (when revision-id (Long. ^String revision-id))
                           :format metadata-format
                           :collection-concept-id collection-concept-id
                           :echo_dataset_id echo_dataset_id
                           :echo_granule_id echo_granule_id
                           :granule-count (when granule-count (Long. ^String granule-count))
                           :has-granules (when has-granules (= has-granules "true"))
                           :metadata metadata})))
                     (cx/elements-at-path parsed [:result])
                     metadatas)
          facets (f/parse-facets-xml (cx/element-at-path parsed [:facets]))]
      (util/remove-nil-keys {:items items
                             :facets facets
                             :hits hits
                             :scroll-id scroll-id
                             :search-after search-after})))))

(defn find-metadata-tags
  "Search metadata, parse out the collection concept id to tags mapping from the search response
  and returns it."
  ([concept-type format-key params]
   (find-metadata-tags concept-type format-key params {}))
  ([concept-type format-key params options]
   (get-search-failure-xml-data
    (let [format-mime-type (mime-types/format->mime-type format-key)
          response (find-concepts-in-format format-mime-type concept-type params options)
          body (:body response)
          parsed (fx/parse-str body)
           ;; First we parse out the metadata and tags from each result, then we parse tags out.
          metadatas (for [match (drop 1 (str/split body #"(?ms)<result "))]
                      (second (re-matches #"(?ms)[^>]*>(.*)</result>.*" match)))
          items (map (fn [result metadata]
                       (let [{{:keys [concept-id]} :attrs} result
                             tags (when-let [tags-metadata (second (str/split metadata #"<tags>"))]
                                    (->> (cx/elements-at-path
                                          (fx/parse-str (str "<tags>" tags-metadata)) [:tag])
                                         (map da/xml-elem->tag)
                                         (into {})))]
                         [concept-id tags]))
                     (cx/elements-at-path parsed [:result])
                     metadatas)]
      (when (seq items)
        (into {} items))))))

(defmulti parse-reference-response
  (fn [echo-compatible? response]
    echo-compatible?))

(defmethod parse-reference-response :default
  [_ response]
  (let [parsed (-> response :body fx/parse-str)
        hits (cx/long-at-path parsed [:hits])
        took (cx/long-at-path parsed [:took])
        scroll-id (get-in response [:headers routes/SCROLL_ID_HEADER])
        search-after (get-in response [:headers routes/SEARCH_AFTER_HEADER])
        refs (map (fn [ref-elem]
                    (util/remove-nil-keys
                     {:id (cx/string-at-path ref-elem [:id])
                      :name (cx/string-at-path ref-elem [:name])
                      :revision-id (cx/long-at-path ref-elem [:revision-id])
                      :location (cx/string-at-path ref-elem [:location])
                      :deleted (cx/bool-at-path ref-elem [:deleted])
                      :granule-count (cx/long-at-path ref-elem [:granule-count])
                      :has-granules (cx/bool-at-path ref-elem [:has-granules])
                      :score (cx/double-at-path ref-elem [:score])}))
                  (cx/elements-at-path parsed [:references :reference]))
        facets (f/parse-facets-xml
                (cx/element-at-path parsed [:facets]))]
    (util/remove-nil-keys
     {:refs refs
      :hits hits
      :scroll-id scroll-id
      :search-after search-after
      :took took
      :facets facets})))

(defmethod parse-reference-response true
  [_ response]
  (let [parsed (-> response :body fx/parse-str)
        references-type (get-in parsed [:attrs :type])
        refs (map (fn [ref-elem]
                    (util/remove-nil-keys
                     {:id (cx/string-at-path ref-elem [:id])
                      :name (cx/string-at-path ref-elem [:name])
                      :location (cx/string-at-path ref-elem [:location])
                      :score (cx/double-at-path ref-elem [:score])}))
                  (cx/elements-at-path parsed [:reference]))]
    (util/remove-nil-keys
     {:refs refs
      :type references-type})))

(defn- parse-reference-response-with-headers
  "Parse the response and include headers"
  [echo-compatible? response]
  (let [headers (:headers response)
        parsed (parse-reference-response echo-compatible? response)]
    (if headers
      (merge parsed {:headers headers})
      parsed)))

(defn- parse-echo-facets-response
  "Returns the parsed facets by parsing the given facets according to catalog-rest facets format"
  [response]
  (let [parsed (-> response :body fx/parse-str)]
    (f/parse-echo-facets-xml parsed)))

(defn- parse-refs-response
  "Parse the find-refs response based on expected format and retruns the parsed result"
  [concept-type params options]
  (let [;; params is not a map for catalog-rest additional attribute style tests,
        ;; we cannot destructing params as a map for the next two lines.
        echo-compatible (:echo-compatible params)
        include-facets (:include-facets params)
        response (find-concepts-in-format mime-types/xml concept-type params options)]
    (if (and echo-compatible include-facets)
      (parse-echo-facets-response response)
      (parse-reference-response echo-compatible response))))

(defn find-refs
  "Returns the references that are found by searching with the input params"
  ([concept-type params]
   (find-refs concept-type params {}))
  ([concept-type params options]
   (get-search-failure-xml-data
    (parse-refs-response concept-type params options))))

(defn find-refs-with-post
  "Returns the references that are found by searching through POST request with the input params"
  [concept-type params]
  (get-search-failure-xml-data
   (let [response (client/post (url/search-url concept-type)
                               {:accept mime-types/xml
                                :content-type mime-types/form-url-encoded
                                :body (codec/form-encode params)
                                :throw-exceptions false
                                :connection-manager (s/conn-mgr)})]
     (parse-reference-response (:echo-compatible params) response))))

(defn find-refs-with-multi-part-form-post
  "Returns the references that are found by searching through POST request with the input form.
  The form parameter should be a vector of maps (one for each field in the form)"
  ([concept-type form]
   (find-refs-with-multi-part-form-post concept-type form {}))
  ([concept-type form options]
   (get-search-failure-xml-data
    (let [response (client/post (url/search-url concept-type)
                                (merge {:accept mime-types/xml
                                        :multipart form
                                        :throw-exceptions true
                                        :connection-manager (s/conn-mgr)}
                                       options))]
      (parse-reference-response-with-headers false response)))))

(defn find-refs-with-json-query
  "Returns the references that are found by searching using a JSON request."
  ([concept-type query-params json-as-map]
   (find-refs-with-json-query concept-type query-params json-as-map {}))
  ([concept-type query-params json-as-map options]
   (get-search-failure-xml-data
    (let [response (find-with-json-query
                    concept-type query-params json-as-map mime-types/xml options)]
      (parse-reference-response (:echo-compatible query-params) response)))))

(defn find-refs-with-aql-string
  ([aql]
   (find-refs-with-aql-string aql {}))
  ([aql options]
   (find-refs-with-aql-string aql options mime-types/xml))
  ([aql options content-type]
   (get-search-failure-xml-data
    (let [response (client/post (url/aql-url)
                                (merge {:accept mime-types/xml
                                        :content-type content-type
                                        :body aql
                                        :query-params {:page-size 100}
                                        :connection-manager (s/conn-mgr)}
                                       options))]
      (parse-reference-response (get-in options [:query-params :echo_compatible]) response)))))

(defn find-refs-with-aql
  "Returns the references that are found by searching through POST request with aql for the given conditions"
  ([concept-type conditions]
   (find-refs-with-aql concept-type conditions {}))
  ([concept-type conditions data-center-condition]
   (find-refs-with-aql concept-type conditions data-center-condition {}))
  ([concept-type conditions data-center-condition options]
   (find-refs-with-aql-string (aql/generate-aql concept-type data-center-condition conditions) options)))

(defn find-refs-with-aql-without-content-type
  "Returns the references that are found by searching through POST request with aql for the given
  conditions without providing a content-type in header."
  [concept-type conditions]
  (find-refs-with-aql-string (aql/generate-aql concept-type {} conditions) {} ""))

(defn find-concepts-with-param-string
  "Returns the concepts by searching with the given parameters string. This is used to execute
  complicated parameter searches where using param map is confusing and less straightforward
  (e.g. for invalid parameter validation)."
  [concept-type param-str]
  (let [url (str (url/search-url concept-type) "?" param-str)]
    (get-search-failure-xml-data
     (parse-reference-response false
                               (client/get url {:connection-manager (s/conn-mgr)})))))

(defn mime-type-matches-response?
  "Checks that the response's content type mime type is the given mime type."
  [response mime-type]
  (= mime-type (mime-types/content-type-mime-type (:headers response))))

(defn provider-holdings-in-format
  "Returns the provider holdings."
  ([format-key]
   (provider-holdings-in-format format-key {} {}))
  ([format-key params]
   (provider-holdings-in-format format-key params {}))
  ([format-key params options]
   (let [format-mime-type (mime-types/format->mime-type format-key)
         {:keys [url-extension]} options
         params (params->snake_case (util/map-keys csk/->snake_case_keyword params))
         echo-compatible? (if (:echo_compatible params) true false)
         [url accept] (if url-extension
                        [(str (url/provider-holdings-url) "." url-extension)]
                        [(url/provider-holdings-url) format-mime-type])
         response (client/get url {:accept accept
                                   :query-params params
                                   :connection-manager (s/conn-mgr)})
         {:keys [status body headers]} response]
     (if (= status 200)
       {:status status
        :headers headers
        :results (ph/parse-provider-holdings format-key echo-compatible? body)}
       response))))

(defn find-tiles
  "Returns the tiles that are found by searching with the input params"
  [params]
  (let [response (client/get (url/search-tile-url) {:query-params params
                                                    :connection-manager (s/conn-mgr)
                                                    :throw-exceptions false})]
    (if (= 200 (:status response))
      {:status (:status response)
       :results (json/decode (:body response))}
      response)))

(defn find-deleted-collections
  "Returns the references that are found by searching deleted collections"
  ([params]
   (find-deleted-collections params nil))
  ([params format-key]
   (let [accept (when format-key
                  (mime-types/format->mime-type format-key))
         response (client/get (url/search-deleted-collections-url)
                              {:query-params params
                               :connection-manager (s/conn-mgr)
                               :accept accept})]
     (parse-reference-response false response))))

(defn find-deleted-granules
  "Returns the references that are found by searching deleted granules"
  ([params]
   (find-deleted-granules params :json))
  ([params format-key]
   (let [accept (when format-key
                  (mime-types/format->mime-type format-key))
         response (client/get (url/search-deleted-granules-url)
                              {:query-params params
                               :connection-manager (s/conn-mgr)
                               :accept accept})]
     response)))

(defn clear-caches
  "Clears caches in the search application"
  []
  (client/post (url/search-clear-cache-url)
               {:connection-manager (s/conn-mgr)
                :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))

(defn get-keywords-by-keyword-scheme
  "Calls the CMR search endpoint to retrieve the controlled keywords for the given keyword scheme."
  ([keyword-scheme]
   (get-keywords-by-keyword-scheme keyword-scheme ""))
  ([keyword-scheme search-parameters]
   (get-search-failure-data
    (let [response (client/get (url/search-keywords-url keyword-scheme search-parameters)
                               {:connection-manager (s/conn-mgr)})
          {:keys [status body]} response]
      (if (= 200 status)
        {:status status
         :results (json/decode body)}
        response)))))

(defn get-humanizers-report-raw
  "Returns the humanizers report."
  ([]
   (get-humanizers-report-raw {}))
  ([params]
   (client/get (url/humanizers-report-url) {:connection-manager (s/conn-mgr)
                                            :query-params params
                                            :throw-exceptions false})))

(defn get-humanizers-report
  "Returns the humanizers report."
  ([]
   (get-humanizers-report {}))
  ([params]
   (let [response (get-humanizers-report-raw params)]
     (if (= 200 (:status response))
       (:body response)
       response))))

(def now-n
  "The N value for the current time. Uses N values for date times as described in
  cmr.common.test.time-util."
  204)

(defn freeze-resume-time-fixture
  []
  (fn [f]
    (try
      ;; Freeze time in test
      (tk/set-time-override! (tu/n->date-time now-n))
      ;; Freeze time on CMR side
      (side/eval-form
       `(do (require 'cmr.common.test.time-util) (tk/set-time-override! (tu/n->date-time ~now-n))))
      (f)
      (finally
        ;; Resume time in test
        (tk/clear-current-time!)
        ;; Resume time on CMR side
        (side/eval-form `(tk/clear-current-time!))))))

(defn- parse-xml-concept-ids
  "Parse concept-ids from xml structure. Use 2-arity to extract from attributes.
  3-arity from path."
  ([response elements-path]
   (map #(get-in % [:attrs :concept-id])
        (cx/elements-at-path (fx/parse-str response) elements-path)))
  ([response elements-path concept-id-path]
   (map #(cx/string-at-path % concept-id-path)
        (cx/elements-at-path (fx/parse-str response) elements-path))))

(defn search-concept-ids-in-format
  "Call search in format and return concept-ids from those results."
  ([format concept-type params]
   (search-concept-ids-in-format format concept-type params {}))
  ([format concept-type params options]
   (let [response (:body (find-concepts-in-format
                          (mime-types/format->mime-type format)
                          concept-type
                          params
                          options))]
     (case format
       :atom (parse-xml-concept-ids response [:entry] [:id])
       :dif (parse-xml-concept-ids response [:result])
       :dif10 (parse-xml-concept-ids response [:result])
       :echo10 (parse-xml-concept-ids response [:result])
       :iso19115 (parse-xml-concept-ids response [:result])
       :json (map :id (get-in (json/decode response true) [:feed :entry]))
       :opendata (map :identifier (:dataset (json/decode response true)))
       :xml (parse-xml-concept-ids response [:references :reference] [:id])
       :umm-json (map #(get-in % [:meta :concept-id]) (:items (json/decode response true)))))))

(defn clear-scroll
  "Executes a search request to clear the scroll context for the given scroll id"
  [scroll-id]
  (get-search-failure-data
   (client/post (url/clear-scroll-url)
                {:content-type mime-types/json
                 :body (json/generate-string {:scroll_id scroll-id})
                 :throw-exceptions true
                 :connection-manager (s/conn-mgr)})))

(defn remove-duplicate-points
  [{:keys [points]}]
  (->> (pv/points->rounded-point-map points)
       vals
       (remove #(> (count %) 1))
       (map reverse)))

(defn- depointify
  [point]
  [(.lon point) (.lat point)])

(defn make-excessive-points-without-dups
  "Returns a list of n-ish unique points."
  [n]
  (str/join ","
            (flatten
             (map #(depointify (get (first %) 1))
                  (let [points {:points (map #(p/point %1 %2)
                                             (repeatedly n
                                                         #(first (shuffle (range -180 180))))
                                             (repeatedly n
                                                         #(first (shuffle (range -90 90)))))}]
                    (remove-duplicate-points points))))))

(defn make-excessive-points
  "Returns a list of n points."
  [n]
  (str/join ","
             (interleave
               (repeatedly n
                           #(first (shuffle (range -180 180))))
               (repeatedly n
                           #(first (shuffle (range -90 90)))))))
