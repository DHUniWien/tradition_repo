package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProposedEmendationModel {
    private String text;
    private String authority;
    private Long fromRank;
    private Long toRank;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }

    public Long getFromRank() {
        return fromRank;
    }

    public void setFromRank(Long fromRank) {
        this.fromRank = fromRank;
    }

    public Long getToRank() {
        return toRank;
    }

    public void setToRank(Long toRank) {
        this.toRank = toRank;
    }
}
