package com.werewolf.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.werewolf.model.FriendsResponse;
import com.werewolf.model.Invitation;

import java.util.List;

/** Profil, amis, invitations en partie. */
public class SocialService {

    private final ApiClient api = new ApiClient();
    private static final ObjectMapper M = new ObjectMapper();

    // ----- Profil -----

    public void updateAvatar(String emoji) throws Exception {
        api.post("/me/avatar", ApiClient.body("avatar_url", emoji));
    }

    public void updateEmail(String email) throws Exception {
        api.post("/me/email", ApiClient.body("email", email));
    }

    public void updatePassword(String oldPwd, String newPwd) throws Exception {
        api.post("/me/password", ApiClient.body(
                "old_password", oldPwd,
                "new_password", newPwd
        ));
    }

    // ----- Amis -----

    public FriendsResponse listFriends() throws Exception {
        return api.get("/friends", FriendsResponse.class);
    }

    /** tag attendu : "pseudo#1234" */
    public JsonNode sendFriendRequest(String tag) throws Exception {
        return api.post("/friends/request", ApiClient.body("pseudo", tag));
    }

    public void acceptFriend(int playerId) throws Exception {
        api.post("/friends/accept", ApiClient.body("player_id", playerId));
    }

    public void declineFriend(int playerId) throws Exception {
        api.post("/friends/decline", ApiClient.body("player_id", playerId));
    }

    public void removeFriend(int playerId) throws Exception {
        api.post("/friends/remove", ApiClient.body("player_id", playerId));
    }

    // ----- Invitations -----

    public List<Invitation> listInvitations() throws Exception {
        JsonNode r = api.get("/invitations");
        return M.convertValue(r.path("invitations"), new TypeReference<List<Invitation>>() {});
    }

    public void sendInvitation(int playerId, int sessionId) throws Exception {
        api.post("/invitations/send", ApiClient.body(
                "player_id", playerId,
                "session_id", sessionId
        ));
    }

    public int acceptInvitation(int invitationId) throws Exception {
        JsonNode r = api.post("/invitations/accept", ApiClient.body("invitation_id", invitationId));
        return r.path("session_id").asInt();
    }

    public void declineInvitation(int invitationId) throws Exception {
        api.post("/invitations/decline", ApiClient.body("invitation_id", invitationId));
    }
}
