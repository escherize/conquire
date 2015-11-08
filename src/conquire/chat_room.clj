(ns conquire.chat-room
  (:require [clojure.string :as str]
            [schema.core :as s]
            [crypto.random :refer [base64]]
            [alandipert.enduro :as e]))

(defn id-generator [prefix & [length]]
  (str prefix "_" (base64 (or length 3))))

(defn gen-question-id [] (id-generator "ques"))
(defn gen-room-id     [] (id-generator "room"))
(defn gen-user-id     [] (id-generator "user"))

(defn id? [prefix maybe-id]
  (->> maybe-id
       (re-matches (re-pattern (str prefix ".*")))
       boolean))

(defn question-id? [maybe-id] (id? "ques" maybe-id))
(defn room-id?     [maybe-id] (id? "room" maybe-id))
(defn user-id?     [maybe-id] (id? "user" maybe-id))

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

(def chat-rooms
  (e/file-atom {} "resources/chat-room-store.clj"))

(def chat-room-audiences
  (e/file-atom {} "resources/chat-room-audiences.clj"))

(s/defn ^:always-validate users-in-room :- [UserID]
  [room-id :- RoomID]
  (->> room-id
       (get @chat-room-audiences)
       (filter user-id?)
       vec))

(s/defn ^:always-validate new-room :- App
  [app :- App user-id :- UserID title :- s/Str & [maybe-room-id :- RoomID]]
  (let [room-id (or maybe-room-id (gen-room-id))]
    (assoc app room-id {:id room-id
                        :title title
                        :owner user-id
                        :questions {}})))

(s/defn ^:always-validate ask-question :- App
  [app :- App
   user-id :- UserID
   room-id :- RoomID
   question-text :- s/Str
   & [maybe-question-id]]
  (def *in [app user-id room-id question-text maybe-question-id])
  (let [question-id (or maybe-question-id (gen-question-id))]
    (assoc-in app [room-id :questions question-id]
              {:id question-id
               :text question-text
               :upvotes #{user-id}})))

(s/defn ^:always-validate upvote :- App
  [app :- App
   user-id :- UserID
   room-id :- RoomID
   question-id :- QuestionID]
  (update-in app [room-id :questions question-id :upvotes]
             #(conj % user-id)))

(s/defn ^:always-validate delete-question :- App
  [app :- App
   user-id :- UserID
   room-id :- RoomID
   question-id :- QuestionID]
  (when (= user-id (get-in app [room-id :owner])) ;; user-id is the room owner
    (update-in app [room-id :questions]
               #(dissoc % question-id))))

(s/defn ^:always-validate room-info
  [app :- App
   room-id :- RoomID]
  (if-let [room (get-in app [room-id])]
    room
    {}))
