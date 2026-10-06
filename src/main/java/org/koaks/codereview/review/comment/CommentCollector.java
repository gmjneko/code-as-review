package org.koaks.codereview.review.comment;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comments of one review task. Tool calls may run concurrently within a turn, so adds are
 * thread-safe; the cap bounds what a single review can emit.
 */
public class CommentCollector {

    private final List<CandidateComment> comments = new CopyOnWriteArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final int cap;

    public CommentCollector(int cap) {
        this.cap = cap;
    }

    /** @return the stored comment, or {@code null} when the cap is reached */
    public synchronized CandidateComment add(String path, String content, String existingCode, String suggestionCode,
                                             String category, String severity, int round) {
        if (comments.size() >= cap) {
            return null;
        }
        CandidateComment c = new CandidateComment("c-" + sequence.getAndIncrement(), path, content, existingCode,
                suggestionCode, category, severity, round);
        comments.add(c);
        return c;
    }

    public List<CandidateComment> all() {
        return List.copyOf(comments);
    }

    /** Comments added after the first {@code baseline} entries. */
    public List<CandidateComment> since(int baseline) {
        List<CandidateComment> snapshot = new ArrayList<>(comments);
        return baseline >= snapshot.size() ? List.of() : List.copyOf(snapshot.subList(baseline, snapshot.size()));
    }

    public int size() {
        return comments.size();
    }

    public boolean full() {
        return comments.size() >= cap;
    }

}
