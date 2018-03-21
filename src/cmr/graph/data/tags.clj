(ns cmr.graph.data.tags
  "Functions for working with tags data."
  (:require
   [clojure.data.codec.base64 :as b64]
   [clojure.edn :as edn])
  (:import
   (java.util.zip GZIPInputStream)
   (java.io ByteArrayInputStream)))

(defn gzip-base64-tag->edn
  "Converts a base64 encoded gzipped string to EDN."
  [^String input]
  (-> input
      .getBytes
      b64/decode
      ByteArrayInputStream.
      GZIPInputStream.
      slurp
      edn/read-string))

(comment
 (gzip-base64-tag->edn "H4sIAAAAAAAAAIWPMQvCMBCF/8qRScEGxM3tiAELtSltFXEpoQkhII0ktR1K/7spio4Od8O7997HTcR5Q1vtAh2NDYG2o22p8bJ73nWgD+8UgWnewF/fYJX20btXspdAcqyQ/M11sreDbqz6BRme+DXZAcvE+QCYx+GlqEQGBZZ1yjIO7IglspqX6Q3rVOSwYlggW8Nl+0WaiAzv3SzFSeu8/rxi3BDJQdJ4VTYs6vwC7Me2uQkBAAA="))
