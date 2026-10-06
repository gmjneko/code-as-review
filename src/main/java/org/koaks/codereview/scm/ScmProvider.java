package org.koaks.codereview.scm;

import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.repo.domain.SourceType;

import java.nio.file.Path;

/**
 * Source-control integration for one kind of repository. GitHub/GitLab providers will fetch into a
 * private mirror (PR refs included) and then expose the same {@link PreparedWorkspace} contract.
 */
public interface ScmProvider {

    SourceType sourceType();

    /** Validates a repository definition before it is saved. */
    void validate(CodeRepository repository);

    boolean supports(ReviewTarget target);

    /**
     * Materialises {@code target} for reading.
     *
     * @param taskDir scratch directory owned by the review task; deleted after the run
     */
    PreparedWorkspace prepare(CodeRepository repository, ReviewTarget target, Path taskDir);

}
