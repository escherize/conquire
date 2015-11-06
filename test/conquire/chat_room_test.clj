(ns conquire.chat-room-test
  (:require [conquire.chat-room :refer :all]
            [clojure.test :refer :all]
            [schema.core :as s]))

(deftest app-validation-test
  (testing "theortiecal app passes validation"
    (let [room-id (room-id)
          owner-id (user-id)
          question-id (question-id)
          app {room-id {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{(user-id)}}}}}]
      (is (= app (s/validate App app)))))
  (testing "Add an upvote as a user."
    (let [room-id (room-id)
          owner-id (user-id)
          some-user-id (user-id)
          question-id (question-id)
          app {room-id {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{}}}}}
          upvoted-app {room-id
                       {:id room-id
                        :title "Startup Lecture"
                        :owner owner-id
                        :questions {question-id
                                    {:id question-id
                                     :text "why is sky blue?"
                                     :upvotes #{some-user-id}}}}}]
      (is (= upvoted-app
             (upvote app some-user-id room-id question-id))))))
