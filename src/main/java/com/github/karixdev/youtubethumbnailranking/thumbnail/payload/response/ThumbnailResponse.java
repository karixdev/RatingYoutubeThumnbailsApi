package com.github.karixdev.youtubethumbnailranking.thumbnail.payload.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.karixdev.youtubethumbnailranking.thumbnail.Thumbnail;
import com.github.karixdev.youtubethumbnailranking.user.payload.repsonse.UserResponse;
import lombok.Data;

@Data
public class ThumbnailResponse {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("youtube_video_id")
    private String youtubeVideoId;

    @JsonProperty("url")
    private String url;

    @JsonProperty("added_by")
    @JsonIgnoreProperties({"email", "user_role", "is_enabled"})
    private UserResponse addedBy;

    public ThumbnailResponse(Thumbnail thumbnail) {
        this.id = thumbnail.getId();
        this.youtubeVideoId = thumbnail.getYoutubeVideoId();
        this.url = thumbnail.getUrl();
        this.addedBy = new UserResponse(thumbnail.getAddedBy());
    }
}