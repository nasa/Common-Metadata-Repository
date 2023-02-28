(ns cmr.ingest.services.granule-bulk-update.utils.echo10
  "Contains functions for updating ECHO10 granule xml metadata."
  (:require
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]))

(def ^:private echo10-main-schema-elements
  "Defines the element tags that come after OnlineAccessURLs in ECHO10 Granule xml schema"
  [:GranuleUR :InsertTime :LastUpdate :DeleteTime :Collection :RestrictionFlag :RestrictionComment :DataGranule
   :PGEVersionClass :Temporal :Spatial :OrbitCalculatedSpatialDomains :MeasuredParameters :Platforms :Campaigns
   :AdditionalAttributes :InputGranules :TwoDCoordinateSystem :Price :OnlineAccessURLs :OnlineResources :Orderable
   :DataFormat :Visible :CloudCover :MetadataStandardName :MetadataStandardVersion :AssociatedBrowseImages :AssociatedBrowseImageUrls])

(defn- get-rest-echo10-elements
  "Go through the list of echo 10 elements and return all of the elements after the
  the passed in element."
  [element]
  (loop [elem (first echo10-main-schema-elements)
         left-over-list (rest echo10-main-schema-elements)]
    (cond
      (= elem element) left-over-list
      :else (recur (first left-over-list) (rest left-over-list)))))

(defn links->online-resources
  "Creates online resource URL XML elements from the passed in links."
  [links]
  (for [link links]
    (let [url (:URL link)
          type (:Type link)
          description (:Description link)
          mime-type (:MimeType link)]
      (xml/element :OnlineResource {}
                   (when url (xml/element :URL {} url))
                   (when description (xml/element :Description {} description))
                   (when type (xml/element :Type {} type))
                   (when mime-type (xml/element :MimeType {} mime-type))))))

(defn xml-elem->online-resource
  "Parses and returns XML element for OnlineResource."
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :description description
     :type resource-type
     :mime-type mime-type}))

(defn update-online-resources
  "Returns an OnlineResources node in zipper representation
  where UPDATE-FIELD is updated where the LOCATOR-FIELD has a matching
  key in the VALUE-MAP."
  [online-resources locator-field value-field value-map]
  (let [edn-resources (map xml-elem->online-resource online-resources)
        resources (map #(merge %
                               (when-let [replacement (get value-map (get % locator-field))]
                                 (hash-map value-field replacement)))
                       edn-resources)]
    (xml/element
     :OnlineResources {}
     (for [r resources]
       (let [{:keys [url description type mime-type]} r]
         (xml/element :OnlineResource {}
                      (xml/element :URL {} url)
                      (when description (xml/element :Description {} description))
                      (xml/element :Type {} type)
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn links->online-access-urls
  "Creates online access URL XML elements from the passed in links."
  [links]
  (for [link links]
    (let [url (:URL link)
          description (:Description link)
          mime-type (:MimeType link)]
      (xml/element :OnlineAccessUrl {}
                   (when url (xml/element :URL {} url))
                   (when description (xml/element :URLDescription {} description))
                   (when mime-type (xml/element :MimeType {} mime-type))))))

(defn- xml-elem->online-access-url
  "Parses and returns XML element for OnlineAccessURL"
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:URLDescription])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :url-description description
     :mime-type mime-type}))

(defn update-online-access-urls
  "Returns an OnlineAccessURLs node in zipper representation
  where UPDATE-FIELD is updated where the LOCATOR-FIELD has a matching
  key in the VALUE-MAP."
  [online-access-urls locator-field value-field value-map]
  (let [edn-access-urls (map xml-elem->online-access-url online-access-urls)
        access-urls (map #(merge %
                                 (when-let [replacement (get value-map (get % locator-field))]
                                   (hash-map value-field replacement)))
                         edn-access-urls)]
    (xml/element
     :OnlineAccessURLs {}
     (for [r access-urls]
       (let [{:keys [url url-description mime-type]} r]
         (xml/element :OnlineAccessURL {}
                      (xml/element :URL {} url)
                      (when url-description (xml/element :URLDescription {} url-description))
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn xml-elem->provider-browse
  "Parses and returns XML element for ProviderBrowseUrl"
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        file-size (cx/long-at-path elem [:FileSize])
        description (cx/string-at-path elem [:Description])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :file-size file-size
     :description description
     :mime-type mime-type}))

(defn links->provider-browse-urls
  "Creates provider browse URL XML elements from the passed in links."
  [links]
   (for [link links]
     (let [url (:URL link)
           file-size (:Size link)
           description (:Description link)
           mime-type (:MimeType link)]
       (xml/element :ProviderBrowseUrl {}
                    (when url (xml/element :URL {} url))
                    (when file-size (xml/element :FileSize {} file-size))
                    (when description (xml/element :Description {} description))
                    (when mime-type (xml/element :MimeType {} mime-type))))))

(defn update-browse-image-urls
  "Returns an AssociatedBrowseImageUrls node in zipper representation
  where UPDATE-FIELD is updated where the LOCATOR-FIELD has a matching
  key in the VALUE-MAP."
  [urls locator-field value-field value-map]
  (let [edn-urls (map xml-elem->provider-browse urls)
        new-urls (map #(merge %
                              (when-let [replacement (get value-map (get % locator-field))]
                                (hash-map value-field replacement)))
                      edn-urls)]
    (xml/element
     :AssociatedBrowseImageUrls {}
     (for [r new-urls]
       (let [{:keys [url file-size description mime-type]} r]
         (xml/element :ProviderBrowseUrl {}
                      (xml/element :URL {} url)
                      (when file-size (xml/element :FileSize {} file-size))
                      (when description (xml/element :Description {} description))
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn replace-in-tree
  "Take a parsed granule xml, replace the given node with the provided replacement
  Returns the zipper representation of the updated xml."
  [tree element-tag replacement]
  (let [zipper (zip/xml-zip tree)
        start-loc (zip/down zipper)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (if-let [right-loc (zip/right loc)]
          (cond
            ;; at an OnlineResources element, replace the node with updated value
            (= element-tag (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc replacement) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false))
          (recur loc true))))))

(defn add-in-tree
  "Take a parsed granule xml, add the passed in items to the node at the passed in element.
  If the element exists, place the items at the end of the element's children. If the element
  does not exist, place the items into the correct spot, using the main list at the top.
  Returns the zipper representation of the updated xml."
  [tree element items]
  (let [zipper (zip/xml-zip tree)
        start-loc (-> zipper zip/down)
        rest-of-echo10-elements (seq (get-rest-echo10-elements element))]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (if-let [right-loc (zip/right loc)]
          (cond
            ;; at the passed in element, append to the node with the updated values
            (= element (-> right-loc zip/node :tag))
            (recur (zip/append-child right-loc items) true)

            ;; at an element after the passed in element add to the left
            (some #{(-> right-loc zip/node :tag)} rest-of-echo10-elements)
            (recur (zip/insert-left right-loc (xml/element element {} items)) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false))
          ;; at the end of the file, add to the right
          (recur (zip/insert-right loc (xml/element element {} items)) true))))))

(defn- compare-to-remove-url
  "This function goes through the list of URLs to remove and compares each one
  to the passed in xml represented child. If a match is found nil is returned,
  otherwise the child is returned."
  [child urls-to-remove]
  (when child
    (let [x (xml-elem->online-resource child)]
      (loop [items urls-to-remove match? false]
        (cond
          (= true match?)
          nil

          (nil? (seq items))
          child

          :else
          (let [item (first items)]
            (if (= (:url x) (:URL item))
              (recur (rest items) true)
              (recur (rest items) false))))))))

(defn remove-from-tree
  "Take a parsed granule xml, remove the passed in items from the node at the passed in element.
  Returns the zipper representation of the updated xml."
  [tree node-path-vector urls-to-remove]
  (let [zipper (zip/xml-zip tree)
        element (first node-path-vector)]
    (loop [loc (-> zipper zip/down) done false]
      (if done
        (zip/root loc)
        (if-let [right-loc (zip/right loc)]
          (cond
            ;; when the passed in element is found, check each of the the urls to remove
            ;; against each child element. This builds a list of children URL elements to add back to this
            ;; node where the urls to remove don't exist, essentially removing them.
            (= element (-> right-loc zip/node :tag))
            (let [children (remove nil?
                                   (map #(compare-to-remove-url % urls-to-remove)
                                        (zip/children right-loc)))]
              ;; if children exist then replace the nodes children.
              ;; otherwise remove the node, as it is no longer needed.
              (if (seq children)
                (let [new-node (zip/make-node right-loc (zip/node right-loc) children)]
                  (recur (zip/replace right-loc new-node) true))
                (recur (zip/remove right-loc) true)))
            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false))
          ;; at the end of the file - we are done.
          (recur loc true))))))
