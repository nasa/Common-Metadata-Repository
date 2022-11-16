
(ns cmr.common.generics-documentation
  "Defines utilities for new generic document pipeline. Most functions will deal
   with either returning generic config files, or lists of approved generics."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [cmr.common.config :as cfg]
   [cmr.common.log :as log :refer [error, info]]))

(defn latest-approved-documentation
  "Return a map of all the configured approved generics and their versions
   This list is to control the documentation portion of these
   string for each one.
   Return {:doc-type \"1.2.3\"}"
  []
  (reduce (fn [data item]
            (assoc data (first item) (last (second item))))
          {}
          (cfg/approved-pipeline-documentation)))

(defn read-generic-doc-file
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version]
  (try
    (-> "schemas/%s/v%s/%s.md"
        (format (name generic-keyword) generic-version (name file-name))
        (io/resource)
        (slurp))
    (catch Exception e (info (format "generic %s was skipped" generic-keyword))
           (str ""))))

(defn all-generic-docs
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name]
  (string/join (seq (for [[k,v] (latest-approved-documentation)]
                      (read-generic-doc-file file-name k (str v))))))

(defn get-toc-headers-from-markdown
  "From the markdown file string of either the ingest or search,
  dyanamically create the table of content
  Parameters:
   * generic-markdown: markdown file for a generic's ingest.md or search.md
   Returns: string"
  [generic-markdown]
  (re-seq #"\#+\s+<a\s.*" generic-markdown))

(defn build-markdown-toc
  "Get the markdown file for a generic based on the api string being read in
   Depth is a measure of how many spaces we need from the start of the line for the
   markdown
   Parameters:
   * depth: integer a count of the number of spaces
   * content: string, the content for the toc
   * link: string, the link to the location in the document
   Returns: string"
  [depth content link]
  (str (apply str (repeat depth " ")) (format "* [%s](#%s)\n" link content)))

(defn get-toc-data
  "Parses out the toc information from the api.md to build the markdown for the toc
   Parameters:
   * table-of-content-headers: string list, the headers in the document
   * doc-type: string [ingest | search]
   Returns: string"
  ([table-of-content-headers doc-type spacer]
   (let [depth (spacer (count (re-seq #"#" table-of-content-headers)))
         link (peek (re-find #"=\"(.*)\">" table-of-content-headers))
         content (peek (re-find #">\s+([^<]*)$" table-of-content-headers))]
     (build-markdown-toc depth link content))))

(defn format-generic-toc
  "Return the specific schema's documentation files given the schema keyword name and version number.
   if the file cannot be read, return an empty string which will have no impact on the API document.
   Parameters:
   * file-name: [ingest | search]
   * generic-keyword: [:grid | ...]
   * generic-version: 0.0.1
   Returns: string"
  [file-name generic-keyword generic-version options]
  (let [generic-markdown (read-generic-doc-file file-name generic-keyword generic-version)]
   (if (not= generic-markdown "")
     (-> generic-markdown
             get-toc-headers-from-markdown
             (as-> xs (map #(get-toc-data % (name file-name) (get options :spacer (fn [x] x))) xs))
             (string/join))
     (str ""))))

(defn all-generic-docs-toc
  "Parse over all of the generic documents and return their combined markdown as a string
  Parameters:
  * file-name: [ingest | search]
  Returns: string"
  [file-name options]
  (string/join (seq (for [[k,v] (latest-approved-documentation)] (format-generic-toc file-name k (str v) options)))))

(defn format-toc-into-doc
  "To fit generic docs into the toc a few html tags must be removed, this block will
   exist in one and only one bullet item
   Parameters: the complete markdown for ingest or search toc
   * generic-doc-toc: the full markdown data for the table of contents
   for either ingest or search
   Returns: string"
  [generic-doc-toc]
  (-> generic-doc-toc
      (string/replace #"\s*<\/li>\n?<\/ul>$" "")
      (string/replace-first #"<ul>\s*\n?\s*<li>" "")))

(defn generic-document-versions->markdown
  "Creates a markdown list of all the Generic Documents with the latest version
  numbers suitable for use on an api.md file."
  []
  (reduce (fn [doc item]
            (let [gen-type (name (first item))
                  gen-ver (last item)]
              (format "%s\n* %s: %s" doc gen-type gen-ver)))
          ""
          (latest-approved-documentation)))
