(ns conquire.chat-room
  (:require [clojure.string :as str]
            [schema.core :as s]
            [crypto.random :refer [base64]]))

(defn id-generator [prefix & [length]]
  (str prefix "_" (base64 (or length 3))))

(defn question-id [] (id-generator "ques"))
(defn room-id []     (id-generator "room"))
(defn user-id []     (id-generator "user"))

(defn id? [prefix maybe-id]
  (->> maybe-id
       (re-matches (re-pattern (str prefix ".*")))
       boolean))

(defn question-id? [maybe-id] (id? "ques" maybe-id))
(defn room-id? [maybe-id] (id? "room" maybe-id))
(defn user-id? [maybe-id] (id? "user" maybe-id))

(def Question {:id (s/pred question-id?)
               :text s/Str
               :upvotes #{(s/pred user-id?)}})

(def Room {:id s/Str
           :title s/Str
           :owner (s/pred user-id?)
           :questions {(s/pred question-id?) Question}})

(def App {(s/pred room-id?) Room})

(s/defn ^:always-validate
  upvote
  [app user-id room-id question-id]
  (update-in app [room-id :questions question-id :upvotes]
             #(conj % user-id)))
