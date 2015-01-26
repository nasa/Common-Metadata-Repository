;;; run with lein exec -p create_user.clj
(require '[cmr.bootstrap.db :as db])

(db/create-user)