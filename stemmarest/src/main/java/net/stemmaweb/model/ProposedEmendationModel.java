package net.stemmaweb.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Provides a model for proposing an emendation, and where in the text it should go.
 * If fromRank and toRank are the same, the emendation will be interposed just before that rank.
 */

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProposedEmendationModel {
    /**
     * The reading that is being proposed for this point in the text
     */
    private String text;
    /**
     * The identity of the proposer
     */
    private String authority;
    /**
     * The rank (inclusive) at which the emendation should start
     */
    private Long fromRank;
    /**
     * The rank (exclusive) at which the emendation should end
     */
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
