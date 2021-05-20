(ns cmr.ingest.services.granule-bulk-update.s3.echo10
  "Contains functions to update ECHO10 granule xml for S3 url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.s3.s3-util :as s3-util]))

(def ^:private tags-after
  "Defines the element tags that come after OnlineAccessURLs in ECHO10 Granule xml schema"
  #{:OnlineResources :Orderable :DataFormat :Visible :CloudCover :MetadataStandardName
    :MetadataStandardVersion :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(defn- is-s3?
  "Returns true if the given url is a S3 url."
  [url]
  (string/starts-with? url "s3://"))

(defn- xml-elem->online-access-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :description description
     :mime-type mime-type}))

(defn- xml-elem->online-access-urls
  "Returns online-resource-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->online-access-url
                  (cx/elements-at-path
                   xml-struct
                   [:OnlineAccessURLs :OnlineAccessURL]))]
    (when (seq urls)
      urls)))

(defn- urls->s3-urls
  "Returns the parsed S3 url maps for the given s3 url strings."
  [urls]
  (for [url urls]
    {:url url :description s3-util/S3_RELATEDURL_DESCRIPTION}))

(defn- updated-online-accesses
  "Take the parsed OnlineAccessURLs in the original metadata, update the existing s3 urls if any
   with the given list of urls."
  [online-accesses urls]
  (let [other-urls (remove #(is-s3? (:url %)) online-accesses)
        s3-urls (urls->s3-urls urls)]
    (concat s3-urls other-urls)))

(defn- appended-online-accesses
  "Takes a list of online-accesses, creates new urls, transforming them
  into online-accesses and appends them."
  [online-accesses urls]
  (let [s3-resources (filter #(is-s3? (:url %)) online-accesses)
        new-s3-urls (clojure.set/difference (set urls) (set (map :url s3-resources)))
        new-s3-resources (urls->s3-urls (set new-s3-urls))]
    (concat new-s3-resources online-accesses)))

(defn- updated-zipper-node
  "Take the parsed OnlineAccessURLs in the original metadata and the desired S3 url,
   return the updated OnlineAccessURLs xml element that can be used by zipper to update the xml."
  [online-accesses urls operation]
  (let [accesses (case operation
                   :replace (updated-online-accesses online-accesses urls)
                   :append (appended-online-accesses online-accesses urls))]
    (xml/element
     :OnlineAccessURLs {}
     (for [a accesses]
       (let [{:keys [url description mime-type]} a]
         (xml/element :OnlineAccessURL {}
                      (xml/element :URL {} url)
                      (when description (xml/element :URLDescription {} description))
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn- url->online-access-elem
  "Returns the OnlineAccessURL xml element for the given url"
  [url]
  (xml/element :OnlineAccessURL {}
               (xml/element :URL {} url)
               (xml/element :URLDescription {} s3-util/S3_RELATEDURL_DESCRIPTION)))

(defn- add-online-accesses
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn urls]
  (insert-fn loc
             (xml/element :OnlineAccessURLs {}
                          (for [url urls]
                            (url->online-access-elem url)))))

(defn- add-s3-url*
  "Take a parsed granule xml, add an OnlineAccessURLs node with the given parsed S3 urls.
  Returns the zipper representation of the updated xml.

  Valid operations
  :replace
  :append"
  [parsed online-accesses urls operation]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at the end of the file, add to the right
            (nil? right-loc)
            (recur (add-online-accesses loc zip/insert-right urls) true)

            ;; at an OnlineAccessURLs element, replace the node with updated value
            (= :OnlineAccessURLs (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc (updated-zipper-node online-accesses urls operation))
                   true)

            ;; at an element after OnlineAccessURLs, add to the left
            (some tags-after [(-> right-loc zip/node :tag)])
            (recur (add-online-accesses right-loc zip/insert-left urls) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn- update-s3-url-metadata
  "Takes the granule ECHO10 xml and a list of S3 urls.
  Update the ECHO10 granule metadata with the S3 urls.
  Returns the updated metadata.

  Valid operators are
  :replace
  :append"
  [gran-xml urls operation]
  (let [parsed (xml/parse-str gran-xml)
        online-accesses (xml-elem->online-access-urls parsed)]
    (xml/indent-str (add-s3-url* parsed online-accesses urls operation))))

(defn update-s3-url
  "Takes the ECHO10 granule concept and a list of S3 urls.
  Update the ECHO10 granule metadata with the S3 urls.
  Returns the granule concept with the updated metadata."
  [concept urls]
  (let [updated-metadata (update-s3-url-metadata (:metadata concept) urls :replace)]
    (assoc concept :metadata updated-metadata)))

(defn append-s3-url
  "Append the ECHO10 granule metadata with the S3 urls. Existing URLs
  will be preserved. If the urls contains a URL already listed, it will
  be ignored.
  Returns the granule concept with the updated metadata."
  [concept urls]
  (let [updated-metadata (update-s3-url-metadata (:metadata concept) urls :append)]
    (assoc concept :metadata updated-metadata)))
