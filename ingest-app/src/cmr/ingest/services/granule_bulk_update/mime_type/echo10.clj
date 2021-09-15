(ns cmr.ingest.services.granule-bulk-update.mime-type.echo10
  "Contains functions to update ECHO10 granule xml for OnlineResource MimeType bulk update."
  (:require
   [clojure.data.xml :as xml]
   [clojure.zip :as zip]
   [cmr.common.xml :as cx]))

(defn- xml-elem->online-resource
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

(defn- update-resources
  "Take the parsed online resources in the original metadata and the desired OPeNDAP urls,
   return the updated online resources xml element that can be used by zipper to update the xml."
  [online-resources url-map]
  (let [edn-resources (map xml-elem->online-resource online-resources)
        resources (map #(merge %
                               (when-let [mime-type (get url-map (:url %))]
                                 {:mime-type mime-type}))
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

(defn- update-xml
  "Take a parsed granule xml, add an OnlineResources node with the given parsed OPeNDAP url.
  Returns the zipper representation of the updated xml."
  [replacement parsed]
  (let [zipper (zip/xml-zip parsed)
        start-loc (zip/down zipper)]
    (loop [loc start-loc done false]
      (if done
        (zip/root loc)
        (let [right-loc (zip/right loc)]
          (cond
            ;; at an OnlineResources element, replace the node with updated value
            (= :OnlineResources (-> right-loc zip/node :tag))
            (recur (zip/replace right-loc replacement) true)

            ;; no action needs to be taken, move to the next node
            :else
            (recur right-loc false)))))))

(defn update-mime-type
  [concept url-map]
  (let [parsed (xml/parse-str (:metadata concept))
        online-resources (cx/elements-at-path
                          parsed
                          [:OnlineResources :OnlineResource])
        updated-metadata (-> online-resources
                             (update-resources url-map)
                             (update-xml parsed)
                             xml/indent-str)]
    (assoc concept :metadata updated-metadata)))
