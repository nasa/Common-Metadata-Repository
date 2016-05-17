(ns cmr.transmit.access-control
  "This contains functions for interacting with the access control API."
  (:require [cmr.transmit.connection :as conn]
            [cmr.transmit.config :as config]
            [ring.util.codec :as codec]
            [cmr.transmit.http-helper :as h]
            [cheshire.core :as json]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL functions

(defn- groups-url
  [conn]
  (format "%s/groups" (conn/root-url conn)))

(defn- group-url
  [conn group-id]
  (str (groups-url conn) "/" group-id))

(defn members-url
  [conn group-id]
  (str (group-url conn group-id) "/members"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request functions

(h/defresetter reset :access-control)

; Group CRUD functions
(h/defcreator create-group :access-control groups-url)
(h/defsearcher search-for-groups :access-control groups-url)
(h/defupdater update-group :access-control group-url)
(h/defdestroyer delete-group :access-control group-url)
(h/defgetter get-group :access-control group-url)

(defn- modify-members
  "Helper functions for adding or removing members"
  [context action concept-id members {:keys [raw? token http-options]}]
  (let [token (or token (:token context))
        headers (when token {config/token-header token})]
    (h/request context :access-control
               {:url-fn #(members-url % concept-id)
                :method action
                :raw? raw?
                :http-options (merge {:body (json/generate-string members)
                                      :content-type :json
                                      :headers headers
                                      :accept :json}
                                     http-options)})))

(defn add-members
  "Adds members to the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id members]
   (add-members context concept-id members nil))
  ([context concept-id members options]
   (modify-members context :post concept-id members options)))

(defn remove-members
  "Removes specified members from the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id members]
   (remove-members context concept-id members nil))
  ([context concept-id members options]
   (modify-members context :delete concept-id members options)))

(defn get-members
  "Gets a list of the members in the group. Valid options are
  * :raw? - set to true to indicate the raw response should be returned. See
  cmr.transmit.http-helper for more info. Default false.
  * token - the user token to use. If not set the token in the context will
  be used.
  * http-options - Other http-options to be sent to clj-http."
  ([context concept-id]
   (get-members context concept-id nil))
  ([context concept-id {:keys [raw? token http-options]}]
   (let [token (or token (:token context))
         headers (when token {config/token-header token})]
     (h/request context :access-control
                {:url-fn #(members-url % concept-id)
                 :method :get
                 :raw? raw?
                 :http-options (merge {:headers headers
                                       :accept :json}
                                      http-options)}))))

;;; ACL Functions

(h/defcreator create-acl :access-control (fn [ctx] (str (conn/root-url ctx) "/acls/")))

(h/defgetter get-acl :access-control (fn [ctx concept-id] (str (conn/root-url ctx) "/acls/" concept-id)))

;;; Misc. Functions

;; Defines health check function
(h/defhealther get-access-control-health :access-control 2)

