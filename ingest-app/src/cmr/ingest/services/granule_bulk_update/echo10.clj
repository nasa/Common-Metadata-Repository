(ns cmr.ingest.services.granule-bulk-update.echo10
  "Contains functions to update ECHO10 granule xml for OPeNDAP url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.xml :as cx]))

(def ^:private OPENDAP_RESOURCE_TYPE
  "OnlineResource Type of OPenDAP url in ECHO10 granule schema"
  "GET DATA : OPENDAP DATA")

(def ^:private tags-after
  "Defines the element tags that come after OnlineResources in ECHO10 Granule xml schema"
  #{:Orderable :DataFormat :Visible :CloudCover :MetadataStandardName :MetadataStandardVersion
    :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(def ^:private cloud-pattern
  "Defines the Hyrax-in-the-cloud pattern for OPeNDAP url"
  (re-pattern "https://opendap.*\\.earthdata\\.nasa\\.gov/.*"))

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

(defn- url->opendap-type
  "Returns the OPeNDAP type of the given url. It would be either :cloud or :on-prem for valid urls."
  [url]
  (when url
    (if (re-matches cloud-pattern url)
      :cloud
      :on-prem)))

(defn- update-opendap-resource
  "Returns the online resource with given OPeNDAP url merged into the online-resource."
  [url online-resource]
  (if url
    (if online-resource
      (assoc online-resource :url url)
      {:url url :type OPENDAP_RESOURCE_TYPE})
    online-resource))

(defn- resources->updated-resource
  "Returns the updated OPeNDAP type (cloud or on-prem) resource based on the opendap resources,
  the opendap type  and the url-map that is parsed from the update url value."
  [opendap-type opendap-resources url-map]
  (->> opendap-resources
       (filter #(= opendap-type (url->opendap-type (:url %))))
       first
       (update-opendap-resource (opendap-type url-map))))

(defn- updated-online-resources
  "Take the parsed online resources in the original metadata, update the existing opendap url
   with the given url-map that is parsed from the url value."
  [online-resources url-map]
  (let [opendap-resources (filter #(is-opendap? (:type %)) online-resources)
        other-resources (remove #(is-opendap? (:type %)) online-resources)
        cloud-resource (resources->updated-resource :cloud opendap-resources url-map)
        on-prem-resource (resources->updated-resource :on-prem opendap-resources url-map)
        updated-resources (conj other-resources cloud-resource on-prem-resource)]
    (remove nil? updated-resources)))

(defn- updated-zipper-resources
  "Take the parsed online resources in the original metadata and the desired OPeNDAP url,
   returns the updated online resources xml element that can be used by zipper to update the xml. "
  [online-resources url-map]
  (let [resources (updated-online-resources online-resources url-map)]
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
  "Takes in a zipper location, call the given insert function and url values to add OnlineResources"
  [loc insert-fn urls]
  (insert-fn loc
             (xml/element :OnlineResources {}
                          (for [url urls]
                            (url->online-resource-elem url)))))

(defn- validate-url
  "Validate the given url. It can be no more than two urls separated by comma, and no more than
  one url matches the pattern https://opendap.*.earthdata.nasa.gov/* and no more than one that
  does not match the pattern.
  Returns the parsed urls in a map under keys :cloud and :on-prem with the corresponding urls."
  [url]
  (let [urls (map string/trim (string/split url #","))]
    (if (> (count urls) 2)
      (errors/throw-service-errors
       :invalid-data [(str "Invalid URL value, no more than two urls can be provided: " url)])
      (let [grouped-urls (group-by url->opendap-type urls)]
        (when (> (count (:cloud grouped-urls)) 1)
          (errors/throw-service-errors
           :invalid-data
           [(str "Invalid URL value, no more than one Hyrax-in-the-cloud OPeNDAP url can be provided: "
                 url)]))
        (when (> (count (:on-prem grouped-urls)) 1)
          (errors/throw-service-errors
           :invalid-data
           [(str "Invalid URL value, no more than one on-prem OPeNDAP url can be provided: "
                 url)]))
        grouped-urls))))

(defn- add-opendap-url*
  "Take a parsed granule xml, add an OnlineResources node with the given OPeNDAP url.
   Returns the zipper representation of the updated xml."
  [parsed online-resources url]
  (let [grouped-urls (validate-url url)
        zipper (zip/xml-zip parsed)
        start-loc (-> zipper zip/down)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at the end of the file, add to the right
            (nil? right-loc)
            (recur (add-online-resources loc zip/insert-right (vals grouped-urls)) true)

            ;; at an OnlineResources element, replace the node with updated value
            (= :OnlineResources (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc (updated-zipper-resources online-resources grouped-urls)) true)

            ;; at an element after OnlineResources, add to the left
            (some tags-after [(-> right-loc zip/node :tag)])
            (recur (add-online-resources right-loc zip/insert-left (vals grouped-urls)) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn add-opendap-url
  "Takes the granule ECHO10 xml and an OPeNDAP url and returns the updated xml
   with an OnlineResources element added for the OPeNDAP url.
   The OPeNDAP url value provided in the granule bulk update request can be comma separated urls.
   But it can have at most two urls, one is on-prem and the other Hyrax-in-the-cloud url.
   The exact url type is determined by matching the url against the same pattern above.
   During update, the Hyrax-in-the-cloud url will overwrite any existing Hyrax-in-the-cloud
   OPeNDAP url in the granule metadata; the on-prem url will overwrite any existing on-prem
   OPeNDAP url in the granule metadata.
   Returns error in the format of {:error error-message} if there are any validation errors."
  [gran-xml value]
  (let [parsed (xml/parse-str gran-xml)
        online-resources (xml-elem->online-resources parsed)]
    (xml/indent-str (add-opendap-url* parsed online-resources value))))
