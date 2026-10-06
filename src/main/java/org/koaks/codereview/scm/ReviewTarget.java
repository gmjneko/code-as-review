package org.koaks.codereview.scm;

/** What a review run looks at. Remote variants are modelled now so providers can grow into them. */
public sealed interface ReviewTarget {

    /** Uncommitted changes (staged, unstaged and untracked) of a local checkout against HEAD. */
    record LocalWorkingTree() implements ReviewTarget {
    }

    /** Changes introduced by {@code head} since it diverged from {@code base}. */
    record CommitRange(String base, String head) implements ReviewTarget {
    }

    record PullRequest(String number) implements ReviewTarget {
    }

    record Issue(String number) implements ReviewTarget {
    }

}
