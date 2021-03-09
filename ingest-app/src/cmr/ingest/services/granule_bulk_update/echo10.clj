(ns cmr.ingest.services.granule-bulk-update.echo10
  "Contains functions to update ECHO10 granule xml for OPeNDAP url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.data.zip.xml :as zx]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]))

(def ^:private OPENDAP_RESOURCE_TYPE
  "OnlineResource Type of OPenDAP url in ECHO10 granule schema"
  "GET DATA : OPENDAP DATA")

(def ^:private tags-after
  "Defines the element tags that come after OnlineResources in ECHO10 Granule xml schema"
  #{:Orderable :DataFormat :Visible :CloudCover :MetadataStandardName :MetadataStandardVersion
    :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(defn- is-opendap?
  "Returns true if the given online resource type is of OPeNDAP.
   An online resource is of OPeNDAP type if the resource type contains OPENDAP (case sensitive)"
  [resource-type]
  (string/includes? resource-type "OPENDAP"))

(defn- xml-elem->online-resource-url
  [elem]
  (let [url (cx/string-at-path elem [:URL])
        description (cx/string-at-path elem [:Description])
        resource-type (cx/string-at-path elem [:Type])
        mime-type (cx/string-at-path elem [:MimeType])]
    {:url url
     :description description
     :type resource-type
     :mime-type mime-type}))

(defn- xml-elem->online-resources
  "Returns online-resource-urls elements from a parsed XML structure"
  [xml-struct]
  (let [urls (map xml-elem->online-resource-url
                  (cx/elements-at-path
                   xml-struct
                   [:OnlineResources :OnlineResource]))]
    (when (seq urls)
      urls)))

(defn- validate-online-resources
  "Returns error if there are more than one online resources with type OPENDAP_RESOURCE_TYPE"
  [resources]
  (let [opendap-resources (filter #(is-opendap? (:type %)) resources)
        opendap-count (count opendap-resources)]
    (when (> opendap-count 1)
      {:errors [(format "There can be no more than one OPeNDAP resource URL in the metadata, but there are %d."
                        opendap-count)]})))


(defn- update-opendap-resource
  "Returns the online resource with URL set to the given value if its type is OPENDAP_RESOURCE_TYPE"
  [online-resource url]
  (if (is-opendap? (:type online-resource))
    (assoc online-resource :url url)
    online-resource))

(defn- updated-online-resources
  "Take the parsed online resources in the original metadata, update the existing opendap url
   with the given value or add an opendap resource if there is none. "
  [online-resources url]
  (let [updated (map #(update-opendap-resource % url) online-resources)]
    (if (some #(is-opendap? (:type %)) updated)
      updated
      (conj updated {:url url :type OPENDAP_RESOURCE_TYPE}))))

(defn- updated-zipper-resources
  "Take the parsed online resources in the original metadata and the desired OPeNDAP url,
   returns the updated online resources xml element that can be used by zipper to update the xml. "
  [online-resources url]
  (let [resources (updated-online-resources online-resources url)]
    (xml/element
     :OnlineResources {}
     (for [r resources]
       (let [{:keys [url description type mime-type]} r]
         (xml/element :OnlineResource {}
                      (xml/element :URL {} url)
                      (when description (xml/element :Description {} description))
                      (xml/element :Type {} type)
                      (when mime-type (xml/element :MimeType {} mime-type))))))))

(defn- url->online-resource-elem
  "Returns the OnlineResource xml element for the given url"
  [url]
  (xml/element :OnlineResource {}
               (xml/element :URL {} url)
               (xml/element :Type {} OPENDAP_RESOURCE_TYPE)))

(defn- add-online-resources
  "Takes in a zipper location, call the given insert function and url value to add OnlineResources"
  [loc insert-fn url]
  (insert-fn loc
             (xml/element :OnlineResources {} (url->online-resource-elem url))))

(defn- add-opendap-url*
  "Take a parsed granule xml, add an OnlineResources node with the given OPeNDAP url.
   Returns the zipper representation of the updated xml."
  [parsed online-resources url]
  (let [zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at the end of the file, add to the right
            (nil? right-loc)
            (recur (add-online-resources loc zip/insert-right url) true)

            ;; at an OnlineResources element, replace the node with updated value
            (= :OnlineResources (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc (updated-zipper-resources online-resources url)) true)

            ;; at an element after OnlineResources, add to the left
            (some tags-after [(-> right-loc zip/node :tag)])
            (recur (add-online-resources right-loc zip/insert-left url) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn add-opendap-url
  "Takes the granule ECHO10 xml and an OPeNDAP url and returns the updated xml
   with an OnlineResources element added for the OPeNDAP url.
   Returns error in the format of {:error error-message} if there are any validation errors."
  [gran-xml value]
  (let [parsed (xml/parse-str gran-xml)
        online-resources (xml-elem->online-resources parsed)]
    (if-let [errors (validate-online-resources online-resources)]
      errors
      (xml/indent-str (add-opendap-url* parsed online-resources value)))))
