(ns cmr.ingest.services.granule-bulk-update.opendap.echo10
  "Contains functions to update ECHO10 granule xml for OPeNDAP url bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.string :as string]
   [clojure.zip :as zip]
   [cmr.common.services.errors :as errors]
   [cmr.common.util :refer [remove-nil-keys]]
   [cmr.common.xml :as cx]
   [cmr.ingest.services.granule-bulk-update.opendap.opendap-util
    :as opendap-util
    :refer [cloud-url? is-opendap?]]))

(def ^:private OPENDAP_RESOURCE_TYPE_MAJOR
  "OnlineResource Type of OPenDAP url in ECHO10 granule schema is used for referencing tool access
   Distinct from 'GET DATA : OPENDAP DATA' which is use for direct downloads, which are placed under the OnlineAccess element"
  "USE SERVICE API")

(def ^:private OPENDAP_RESOURCE_TYPE_MINOR
  "Minor Type string"
  "OPENDAP DATA")

(def ^:private OPENDAP_RESOURCE_TYPE
  (str OPENDAP_RESOURCE_TYPE_MAJOR " : " OPENDAP_RESOURCE_TYPE_MINOR))

(def ^:private tags-after
  "Defines the element tags that come after OnlineResources in ECHO10 Granule xml schema"
  #{:Orderable :DataFormat :Visible :CloudCover :MetadataStandardName :MetadataStandardVersion
    :AssociatedBrowseImages :AssociatedBrowseImageUrls})

(defn- xml-elem->online-resource-url
  "Parses and returns XML element for OnlineResource"
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

(defn- update-resource-type
  "Transforms and returns the Type value, where the minor type is preserved."
  [type-value]
  (if type-value
    (let [trim-with-default (fnil string/trim OPENDAP_RESOURCE_TYPE_MINOR)
          minor (trim-with-default (second (string/split type-value #":")))]
      (str OPENDAP_RESOURCE_TYPE_MAJOR " : " minor))
    OPENDAP_RESOURCE_TYPE))

(defn- update-opendap-resource
  "Returns the online resource with given OPeNDAP url merged into the online-resource."
  [url online-resource]
  (if url
    (if online-resource
      (-> online-resource
          (assoc :url url)
          (update :type update-resource-type)
          remove-nil-keys)
      {:url url :type OPENDAP_RESOURCE_TYPE})
    online-resource))

(defn- resources->updated-resource
  "Returns the updated OPeNDAP type (cloud or on-prem) resource based on the opendap resources,
  the opendap type and the url-map that is parsed from the update url value."
  [opendap-type opendap-resources url-map]
  (let [resources (filter #(= opendap-type (opendap-util/url->opendap-type (:url %))) opendap-resources)]
    (when (< 1 (count resources))
      (errors/throw-service-errors :invalid-data
                                   [(str "Cannot update granule - more than one Hyrax-in-the-cloud or"
                                         " more than one on-prem OPeNDAP link was detected in the granule")]))
    (update-opendap-resource (first (opendap-type url-map)) (first resources))))

(defn- updated-online-resources
  "Take the parsed online resources in the original metadata, update the existing opendap url
   with the given url-map that is parsed from the url value."
  [online-resources url-map]
  (let [opendap-resources (filter #(is-opendap? (:type %)) online-resources)
        non-opendap (remove #(is-opendap? (:type %)) online-resources)
        other-resources (for [resource non-opendap]
                          (if (get url-map (:type resource))
                            (update-opendap-resource (:url resource) resource)
                            resource))
        cloud-resource (resources->updated-resource :cloud opendap-resources url-map)
        on-prem-resource (resources->updated-resource :on-prem opendap-resources url-map)
        updated-resources (conj other-resources cloud-resource on-prem-resource)]
    (remove nil? updated-resources)))

(defn- appended-online-resources
  "Append urls only if there are no conflicting URLs."
  [online-resources url-map]
  (let [opendap-resources (filter #(is-opendap? (:type %)) online-resources)
        other-resources (remove #(is-opendap? (:type %)) online-resources)
        current-opendap-urls (map :url opendap-resources)

        _ (opendap-util/validate-append-no-conflicts current-opendap-urls url-map)

        cloud-resource (resources->updated-resource :cloud opendap-resources url-map)
        on-prem-resource (resources->updated-resource :on-prem opendap-resources url-map)
        updated-resources (conj other-resources cloud-resource on-prem-resource)]
    (remove nil? updated-resources)))

(defn- updated-zipper-resources
  "Take the parsed online resources in the original metadata and the desired OPeNDAP urls,
   return the updated online resources xml element that can be used by zipper to update the xml."
  [online-resources url-map operation]
  (let [resources (case operation
                    :replace (updated-online-resources online-resources url-map)
                    :append (appended-online-resources online-resources url-map))]
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
        start-loc (zip/down zipper)]
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

(defn- update-opendap-url-metadata
  "Takes the granule ECHO10 xml and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  Update the ECHO10 granule metadata with the OPeNDAP urls.
  Returns the updated metadata."
  [gran-xml grouped-urls operation]
  (let [parsed (xml/parse-str gran-xml)
        online-resources (xml-elem->online-resources parsed)]
    (xml/indent-str (add-opendap-url* parsed online-resources grouped-urls operation))))

(defn update-opendap-url
  "Takes the ECHO10 granule concept and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>] <OTHER UPDATE> [<URLS to update>]}.
  Update the ECHO10 granule metadata with the OPeNDAP urls.
  Returns the granule concept with the updated metadata."
  [concept grouped-urls]
  (let [updated-metadata (update-opendap-url-metadata (:metadata concept) grouped-urls :replace)]
    (assoc concept :metadata updated-metadata)))

(defn- filter-opendap-resources
  "Takes a list of OnlineResource and returns the OPeNDAP links as a map of {:cloud [<url>] :on-prem [<url>]}"
  [online-resources]
  (->> online-resources
       (filter #(is-opendap? (:type %)))
       (map #(if (cloud-url? (:url %))
               {:cloud [(:url %)]}
               {:on-prem [(:url %)]}))
       (apply merge)))

(defn- filter-other-resources
  "Takes a list of OnlineResource and returns the ones that match the type as a map of {:other [<url>, <url>]}"
  [online-resources target-type]
  (let [resources (->> online-resources
                       (filter #(= target-type (:type %)))
                       (map :url))]
    {target-type resources}))

(defn update-opendap-type
  "Takes the ECHO10 granule concept and updates all OPeNDAP link Types elements to be the string
   designated by [[OPENDAP_RESOURCE_TYPE]]

   Optionally a specific link type may be specified to be updated. When specified only that type
   will be updated."
  ([concept]
   (update-opendap-type concept nil))
  ([concept target-type]
   (let [parsed (xml/parse-str (:metadata concept))
         online-resources (xml-elem->online-resources parsed)
         resources (if target-type
                     (filter-other-resources online-resources target-type)
                     (filter-opendap-resources online-resources))]
     (update-opendap-url concept resources))))

(defn append-opendap-url
  "Takes the ECHO10 granule concept and grouped OPeNDAP urls in the format of
  {:cloud [<cloud_url>] :on-prem [<on_prem_url>]}.
  Appends the ECHO10 granule metadata with the OPeNDAP urls.

  This will fail if there is already a URL present for a given type (:cloud or :on-prep)
  as there may only be a single value for each type and a maximum of two URLS.

  Returns the granule concept with the updated metadata."
  [concept grouped-urls]
  (let [updated-metadata (update-opendap-url-metadata (:metadata concept) grouped-urls :append)]
    (assoc concept :metadata updated-metadata)))
