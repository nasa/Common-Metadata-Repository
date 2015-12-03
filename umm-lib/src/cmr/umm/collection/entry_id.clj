(ns cmr.umm.collection.entry-id
  "Functions to create and retrieve entry-id for collections."
  (:require [cmr.umm.collection :as c]
            [clojure.string :as str]))

(def DEFAULT_VERSION "Not provided")

(defn entry-id
  "Returns the entry-id for the given short-name and version-id."
  [short-name version-id]
  (if (not= DEFAULT_VERSION version-id)
     (str short-name "_V:" version-id)
     short-name))

(defn umm->entry-id
  "Returns an entry-id for the given umm record."
  [umm]
  (let [{:keys [short-name version-id]} (:product umm)]
  	(entry-id short-name version-id)))

(defn entry-id->short-name
  "Convert an entry-id into a short-name by removing any tail starting with _V: - this
  is only used by DIF 9. This is needed for round trip testing that is generating an
  EntryID element from a short-name and version-id and then converting it back to a
  short-name."
  [entry-id]
  ;; TODO - this depends on the short-name and version not containing _V: - I'm not sure
  ;; how else to handle this. This may cause problems with the property based generation.
  (str/replace entry-id #"_V:.*$" ""))