package org.koaks.codereview.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.koaks.codereview.config.CodeReviewProperties;
import org.koaks.codereview.repo.domain.CodeRepository;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagItem;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.scm.github.GitHubClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagAssociationService {
    private final RagIndexer indexer;
    private final HybridRetriever retriever;
    private final RagVerifier verifier;
    private final RagRelationService relations;
    private final RagRelationPublisher publisher;
    private final GitHubClient github;
    private final JsonMapper json;
    private final CodeReviewProperties properties;

    public void process(CodeRepository repo, RagEnums.ItemType sourceType, String sourceNumber) {
        RagIndexer.IndexedItem indexed = indexer.index(repo, sourceType, sourceNumber);
        RagItem source = indexed.item();
        Set<RagReferenceParser.Reference> references = new LinkedHashSet<>();
        indexed.chunks().forEach(c -> references.addAll(RagReferenceParser.parse(c.getTextContent(), repo.getExternalFullName())));
        for (RagReferenceParser.Reference reference : references) {
            if (sourceNumber.equals(reference.number())) continue;
            try { explicit(repo, source, reference); } catch (RuntimeException e) { log.warn("RAG explicit reference failed", e); }
        }

        String query = indexed.chunks().stream().map(c -> c.getTextContent()).reduce((a, b) -> a + "\n\n" + b).orElse(source.getTitle());
        if (sourceType == RagEnums.ItemType.ISSUE) {
            match(repo, source, query, RagEnums.ItemType.ISSUE, true);
            match(repo, source, query, RagEnums.ItemType.PR, false);
        } else {
            match(repo, source, query, RagEnums.ItemType.ISSUE, true);
        }
    }

    public void remove(CodeRepository repo, RagEnums.ItemType type, String number) {
        indexer.remove(repo, type, number);
    }

    private void match(CodeRepository repo, RagItem source, String query, RagEnums.ItemType targetType, boolean prioritizeOpen) {
        List<HybridRetriever.Candidate> candidates = retriever.retrieve(repo.getId(), source.getItemType(), source.getItemNumber(),
                query, targetType, prioritizeOpen);
        int limit = Math.max(1, properties.rag().retrieval().verifyTopK());
        for (HybridRetriever.Candidate candidate : candidates.stream().limit(limit).toList()) {
            try {
                if (source.getItemType() == targetType && source.getItemNumber().equals(candidate.itemNumber())) continue;
                Object original = targetType == RagEnums.ItemType.ISSUE
                        ? github.issue(repo.getExternalFullName(), candidate.itemNumber(), repo.getCredentialId(), repo.getUserId())
                        : github.pullRequest(repo.getExternalFullName(), candidate.itemNumber(), repo.getCredentialId(), repo.getUserId());
                if (original == null) continue;
                RagVerifier.RagEnumsView sourceView = new RagVerifier.RagEnumsView(source.getItemType().name(), source.getItemNumber(),
                        source.getTitle(), source.getBody(), source.getState(), source.getAuthorLogin());
                RagVerifier.RagEnumsView candidateView = view(targetType, original);
                RagVerifier.MatchDecision decision = verifier.verify(repo.getUserId(), sourceView, candidateView,
                        candidate.chunkType() + " by @" + candidate.authorLogin());
                boolean accepted = source.getItemType() == RagEnums.ItemType.ISSUE && targetType == RagEnums.ItemType.ISSUE
                        ? decision.sameProblem() : decision.relevant() || decision.sameProblem();
                if (!accepted) continue;
                RagEnums.RelationKind kind = source.getItemType() == RagEnums.ItemType.ISSUE && targetType == RagEnums.ItemType.ISSUE
                        ? RagEnums.RelationKind.DUPLICATE_ISSUE : RagEnums.RelationKind.RELATED_PR;
                RagRelation row = relations.upsert(repo.getId(), source.getItemType(), source.getItemNumber(), targetType,
                        candidate.itemNumber(), kind, RagEnums.RelationOrigin.RAG, decision.confidence(), decision.reason(),
                        evidence(candidate));
                publisher.publish(repo, row);
            } catch (RuntimeException e) {
                log.warn("RAG candidate verification failed for {} #{}", candidate.itemType(), candidate.itemNumber(), e);
            }
        }
    }

    private void explicit(CodeRepository repo, RagItem source, RagReferenceParser.Reference reference) {
        GitHubClient.GitHubIssue probe = github.issue(repo.getExternalFullName(), reference.number(), repo.getCredentialId(), repo.getUserId());
        if (probe == null) return;
        RagEnums.ItemType targetType = probe.pullRequest() == null ? RagEnums.ItemType.ISSUE : RagEnums.ItemType.PR;
        if (source.getItemType() == targetType && source.getItemNumber().equals(reference.number())) return;
        // Ensure an explicit target is represented in both indexes before writing the relation.
        indexer.index(repo, targetType, reference.number());
        RagRelation row = relations.upsert(repo.getId(), source.getItemType(), source.getItemNumber(), targetType,
                reference.number(), RagEnums.RelationKind.EXPLICIT_REFERENCE, RagEnums.RelationOrigin.EXPLICIT_REFERENCE,
                1d, "GitHub explicit reference " + reference.matchedText(), json.writeValueAsString(Map.of("reference", reference.matchedText())));
        publisher.publish(repo, row);
    }

    private static RagVerifier.RagEnumsView view(RagEnums.ItemType type, Object value) {
        if (type == RagEnums.ItemType.ISSUE) return RagVerifier.RagEnumsView.issue((GitHubClient.GitHubIssue) value);
        return RagVerifier.RagEnumsView.pr((GitHubClient.GitHubPullRequest) value);
    }

    private String evidence(HybridRetriever.Candidate candidate) {
        return json.writeValueAsString(Map.of("backend", candidate.backend(), "chunkType", candidate.chunkType(),
                "commentId", candidate.externalCommentId() == null ? "" : candidate.externalCommentId(),
                "author", candidate.authorLogin() == null ? "" : candidate.authorLogin()));
    }
}
