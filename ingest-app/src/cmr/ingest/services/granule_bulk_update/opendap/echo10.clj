(ns cmr.ingest.services.granule-bulk-update.opendap.echo10
  "Contains functions to update ECHO10 granule xml for OPeNDAP url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util :as opendap-util]))

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
  the opendap type and the url-map that is parsed from the update url value."
  [opendap-type opendap-resources url-map]
  (->> opendap-resources
       (filter #(= opendap-type (opendap-util/url->opendap-type (:url %))))
       first
       (update-opendap-resource (first (opendap-type url-map)))))

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

(defn- cloud-url?
  "Returns true if the URL given matches a Hyrax-in-the-cloud (:cloud) URL pattern "
  [url]
  (= :cloud (opendap-util/url->opendap-type url)))

(def ^:private on-prem-url?
  "Returns true if the URL given matches :on-prem URL patterns"
  (complement cloud-url?))

(defn- appended-online-resources
  "Append urls only if there are no conflicting URLs."
  [online-resources url-map]
  (let [opendap-resources (filter #(is-opendap? (:type %)) online-resources)
        other-resources (remove #(is-opendap? (:type %)) online-resources)
        current-urls (map :url opendap-resources)

        _ (when (and (seq (filter cloud-url? current-urls))
                     (first (get-in url-map [:cloud])))
            (throw (ex-info "Update contains conflict, cannot append Hyrax-in-the-cloud OPeNDAP urls when there is one already present."
                            {:current current-urls
                             :conflict url-map})))

        _ (when (and (seq (filter on-prem-url? current-urls))
                     (first (get-in url-map [:on-prem])))
            (throw (ex-info "Update contains conflict, cannot append on-prem OPeNDAP urls when there is one already present."
                            {:current current-urls
                             :conflict url-map})))

        cloud-resource (resources->updated-resource :cloud opendap-resources url-map)
        on-prem-resource (resources->updated-resource :on-prem opendap-resources url-map)
        updated-resources (conj other-resources cloud-resource on-prem-resource)]
    (remove nil? updated-resources)))

(defn- updated-zipper-resources
  "Take the parsed online resources in the original metadata and the desired OPeNDAP urls,
   return the updated online resources xml element that can be used by zipper to update the xml."
  [online-resources url-map operation]
  (let [resources (if (= :replace operation)
                    (updated-online-resources online-resources url-map)
                    (appended-online-resources online-resources url-map))]
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

(defn- add-opendap-url*
  "Take a parsed granule xml, add an OnlineResources node with the given parsed OPeNDAP url.
  The Hyrax-in-the-cloud url will overwrite any existing Hyrax-in-the-cloud OPeNDAP url in
  the granule metadata; the on-prem url will overwrite any existing on-prem OPeNDAP url in
  the granule metadata.
  Returns the zipper representation of the updated xml."
  [parsed online-resources grouped-urls operation]
  (let [zipper (zip/xml-zip parsed)
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
            (recur (zip/replace right-loc (updated-zipper-resources online-resources grouped-urls operation))
                   true)

            ;; at an element after OnlineResources, add to the left
            (some tags-after [(-> right-loc zip/node :tag)])
            (recur (add-online-resources right-loc zip/insert-left (vals grouped-urls)) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn- add-opendap-url-to-metadata
  "Takes the granule ECHO10 xml and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  Update the ECHO10 granule metadata with the OPeNDAP urls.
  Returns the updated metadata."
  [gran-xml grouped-urls operation]
  (let [parsed (xml/parse-str gran-xml)
        online-resources (xml-elem->online-resources parsed)]
    (xml/indent-str (add-opendap-url* parsed online-resources grouped-urls operation))))

(defn add-opendap-url
  "Takes the ECHO10 granule concept and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  Update the ECHO10 granule metadata with the OPeNDAP urls.
  Returns the granule concept with the updated metadata."
  [concept grouped-urls]
  (let [updated-metadata (add-opendap-url-to-metadata (:metadata concept) grouped-urls :replace)]
    (assoc concept :metadata updated-metadata)))

(defn append-opendap-url
  "Takes the ECHO10 granule concept and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  Update the ECHO10 granule metadata with the OPeNDAP urls.
  Returns the granule concept with the updated metadata."
  [concept grouped-urls]
  (let [updated-metadata (add-opendap-url-to-metadata (:metadata concept) grouped-urls :append)]
    (assoc concept :metadata updated-metadata)))
