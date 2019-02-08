(ns cmr.ingest.api.multipart
  "A modified copy of ring.middleware.multipart-params.
  Specifies a ring middleware that can parse out multipart params along with a content type with
  each string parameter. The original version did not allow extracting a content type unless the
  parameter sent was a file type."
  (:require
   [ring.util.codec :refer [assoc-conj]]
   [ring.util.request :as req])
  (:import
   (org.apache.commons.fileupload FileItemIterator FileItemStream FileUpload UploadContext)
   (org.apache.commons.fileupload.util Streams)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Private methods copied from ring.middleware.multipart-params

(defn- multipart-form?
  "Does a request have a multipart form?"
  [request]
  (= (req/content-type request) "multipart/form-data"))

(defn- request-context
  "Create an UploadContext object from a request map."
  {:tag UploadContext}
  [request encoding]
  (reify UploadContext
    (getContentType [this]       (get-in request [:headers "content-type"]))
    (getContentLength [this]     (or (req/content-length request) -1))
    (contentLength [this]        (or (req/content-length request) -1))
    (getCharacterEncoding [this] encoding)
    (getInputStream [this]       (:body request))))

(defn- file-item-iterator-seq
  "Create a lazy seq from a FileItemIterator instance."
  [^FileItemIterator it]
  (lazy-seq
    (if (.hasNext it)
      (cons (.next it) (file-item-iterator-seq it)))))

(defn- file-item-seq
  "Create a seq of FileItem instances from a request context."
  [context]
  (file-item-iterator-seq
    (.getItemIterator (FileUpload.) context)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions based on existing functions in ring.middleware.multipart-params
;; They have been left as close to their original version as possible.

(defn- parse-file-item
  "Parse a FileItemStream into a key-value pair. The value will be a map containing the
  content-type of the file item and a string of the content."
  [^FileItemStream item]
  [(.getFieldName item)
   {:content-type (.getContentType item)
    :content (Streams/asString (.openStream item))}])

(defn- parse-multipart-params
  "Parse a map of multipart parameters from the request."
  [request]
  (let [encoding (or (req/character-encoding request) "UTF-8")]
   (->> (request-context request encoding)
        file-item-seq
        (map parse-file-item)
        (reduce (fn [m [k v]] (assoc-conj m k v)) {}))))

(defn multipart-params-request
  "Adds :multipart-params and :params keys to request."
  [request]
  (let [params (if (multipart-form? request)
                 (parse-multipart-params request)
                 {})]
    (merge-with merge request
                {:multipart-params params}
                {:params params})))

(defn wrap-multipart-params
  "Middleware to parse multipart parameters from a request. Adds the
  following keys to the request map:

  :multipart-params - a map of multipart parameters
  :params           - a merged map of all types of parameter"
  [handler]
  (fn [request]
    (handler (multipart-params-request request))))
