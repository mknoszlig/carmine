(ns taoensso.carmine.ring
  "Carmine-backed Ring session store. Adapted from clj-redis-session."
  {:author "Peter Taoussanis"}
  (:require [ring.middleware.session.store :as session-store]
            [taoensso.carmine :as car])
  (:import  [java.util UUID]))

(defn new-session-key [prefix] (str prefix ":" (UUID/randomUUID)))
(defmacro wcar* [& body] `(car/wcar @~'conn-atom ~@body))

(defprotocol ICarmineSessionStore (reset-conn [this conn]))

(defrecord CarmineSessionStore [conn-atom prefix expiration]
  ICarmineSessionStore (reset-conn [_ conn] (reset! conn-atom conn))
  session-store/SessionStore
  (read-session   [_ key] (or (when key (wcar* (car/get key))) {}))
  (delete-session [_ key] (wcar* (car/del key)) nil)
  (write-session  [_ key data]
    (let [key (or key (new-session-key prefix))]
      (if expiration
        (wcar* (car/setex key expiration data))
        (wcar* (car/set key data)))
      key)))

(defn carmine-store
  "Creates and returns a Carmine-backed Ring SessionStore. Use `expiration-secs`
  to specify how long session data will survive after last write. When nil,
  sessions will never expire."
  [conn & [{:keys [key-prefix expiration-secs]
            :or   {key-prefix       "carmine:session"
                   expiration-secs  (str (* 60 60 24 30))}}]]
  (->CarmineSessionStore (atom conn) key-prefix (str expiration-secs)))

(defn make-carmine-store ; 1.x backwards compatiblity
  "DEPRECATED. Use `carmine-store` instead."
  [& [s1 s2 & sn :as sigs]]
  (if (instance? taoensso.carmine.connections.ConnectionPool s1)
    (carmine-store {:pool s1 :spec s2} (apply hash-map sn))
    (apply carmine-store sigs)))
