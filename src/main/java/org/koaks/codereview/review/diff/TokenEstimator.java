package org.koaks.codereview.review.diff;

/** Order-of-magnitude token estimate (about four characters per token) for budgeting only. */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    public static int estimate(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }

}
