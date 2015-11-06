(ns conquire.chat-room
  (:require [clojure.string :as str]
            [schema.core :as s]
            [crypto.random :refer [base64]]))

(defn id-generator [prefix & [length]]
  (str prefix "_" (base64 (or length 3))))

(defn gen-question-id [] (id-generator "ques"))
(defn gen-room-id     [] (id-generator "room"))
(defn gen-user-id     [] (id-generator "user"))

(defn id? [prefix maybe-id]
  (->> maybe-id
       (re-matches (re-pattern (str prefix ".*")))
       boolean))

(fn question-id? [maybe-id] (id? "ques" maybe-id))
(fn room-id?     [maybe-id] (id? "room" maybe-id))
(fn user-id?     [maybe-id] (id? "user" maybe-id))

(def QuestionID (s/pred question-id?))
(def RoomID     (s/pred room-id?))
(def UserID     (s/pred user-id?))

(def Question {:id      QuestionID
               :text    s/Str
               :upvotes #{UserID}})

(def Room {:id        s/Str
           :title     s/Str
           :owner     UserID
           :questions {QuestionID Question}})

(def App {RoomID Room})

(s/defn ^:always-validate
  new-room :- App
  [app :- App
   user-id :- UserID
   title :- s/Str
   & [maybe-room-id :- RoomID]]
  (let [room-id (or maybe-room-id (gen-room-id))]
    (assoc app room-id {:id room-id
                        :title title
                        :owner user-id
                        :questions {}})))

(s/defn ^:always-validate
  ask-question :- App
  [app :- App
   user-id :- UserID
   room-id :- RoomID
   question-text :- s/Str
   & [maybe-question-id :- QuestionID]]
  (let [question-id (or maybe-question-id (gen-question-id))]
    (assoc-in app [room-id :questions question-id]
              {:id question-id
               :text question-text
               :upvotes #{}})))

(s/defn ^:always-validate
  upvote :- App
  [app :- App
   user-id :- UserID
   room-id :- RoomID
   question-id :- QuestionID]
  (update-in app [room-id :questions question-id :upvotes]
             #(conj % user-id)))
