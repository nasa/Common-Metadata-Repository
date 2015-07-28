(ns cmr.common.sql-helper
  "This contains helper functions for sql on oracle."
  (:require [clojure.java.io :as io])
  (:import java.util.zip.GZIPInputStream
           java.util.zip.GZIPOutputStream
           java.io.ByteArrayOutputStream
           java.sql.Blob))

(defn- blob->input-stream
  "Convert a BLOB to an InputStream"
  [^Blob blob]
  (.getBinaryStream blob))

(defn blob->string
  "Convert a BLOB to a string"
  [blob]
  (-> blob blob->input-stream GZIPInputStream. slurp))

(defn string->gzip-bytes
  "Convert a string to an array of compressed bytes"
  [input]
  (let [output (ByteArrayOutputStream.)
        gzip (GZIPOutputStream. output)]
    (io/copy input gzip)
    (.finish gzip)
    (.toByteArray output)))
