package com.werewolf.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FriendsResponse {
    public List<Friend> friends;
    public List<FriendRequest> received;
    public List<FriendRequest> sent;
}
