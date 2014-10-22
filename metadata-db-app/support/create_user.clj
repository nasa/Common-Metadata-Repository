;;; run with lein exec -p create_user.clj
(require '[cmr.db :as db])

(db/create-user)