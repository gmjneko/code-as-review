package org.koaks.codereview.rag;

import org.junit.jupiter.api.Test;
import org.koaks.codereview.rag.service.RagReferenceParser;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.service.RagRelationService;

import static org.assertj.core.api.Assertions.assertThat;

class RagReferenceParserTest {
    @Test
    void recognizesLocalOwnerAndUrlReferencesOnlyForTheCurrentRepository() {
        var refs = RagReferenceParser.parse("Fixes #12 and acme/demo#13; see https://github.com/acme/demo/issues/14 and acme/other#15", "acme/demo");
        assertThat(refs).extracting(RagReferenceParser.Reference::number).containsExactly("14", "13", "12");
        assertThat(refs).allMatch(r -> r.repositoryFullName().equals("acme/demo"));
    }

    @Test
    void ignoresSelfReferencesAtTheAssociationLayerInput() {
        var refs = RagReferenceParser.parse("Issue #12 mentions itself and #20", "acme/demo");
        assertThat(refs).extracting(RagReferenceParser.Reference::number).containsExactly("12", "20");
    }

    @Test
    void relationKeysAreCanonicalForIdempotentBidirectionalWriteback() {
        var pair = RagRelationService.canonical(RagEnums.ItemType.PR, "2", RagEnums.ItemType.ISSUE, "3");
        assertThat(pair.sourceType()).isEqualTo(RagEnums.ItemType.ISSUE);
        assertThat(pair.sourceNumber()).isEqualTo("3");
        assertThat(pair.targetType()).isEqualTo(RagEnums.ItemType.PR);
        assertThat(pair.targetNumber()).isEqualTo("2");
    }
}
