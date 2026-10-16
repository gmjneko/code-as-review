package org.koaks.codereview.rag.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.koaks.codereview.rag.domain.RagEnums;
import org.koaks.codereview.rag.domain.RagRelation;
import org.koaks.codereview.rag.mapper.RagRelationMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RagRelationService {
    private final RagRelationMapper mapper;

    public RagRelation upsert(long repositoryId, RagEnums.ItemType sourceType, String sourceNumber,
                              RagEnums.ItemType targetType, String targetNumber, RagEnums.RelationKind kind,
                              RagEnums.RelationOrigin origin, double confidence, String reason, String evidenceJson) {
        Pair pair = canonical(sourceType, sourceNumber, targetType, targetNumber);
        RagRelation relation = mapper.selectOne(Wrappers.<RagRelation>lambdaQuery()
                .eq(RagRelation::getRepositoryId, repositoryId).eq(RagRelation::getSourceType, pair.sourceType())
                .eq(RagRelation::getSourceNumber, pair.sourceNumber()).eq(RagRelation::getTargetType, pair.targetType())
                .eq(RagRelation::getTargetNumber, pair.targetNumber())
                .last("LIMIT 1"));
        if (relation == null) {
            relation = new RagRelation(); relation.setRepositoryId(repositoryId);
            relation.setSourceType(pair.sourceType()); relation.setSourceNumber(pair.sourceNumber());
            relation.setTargetType(pair.targetType()); relation.setTargetNumber(pair.targetNumber());
            relation.setRelationKind(kind); relation.setSourcePublishStatus(RagEnums.PublishStatus.PENDING);
            relation.setTargetPublishStatus(RagEnums.PublishStatus.PENDING); mapper.insert(relation);
        }
        if (relation.getRelationKind() == null) relation.setRelationKind(kind);
        if (origin == RagEnums.RelationOrigin.EXPLICIT_REFERENCE) relation.setOrigin(origin);
        relation.setConfidence(BigDecimal.valueOf(Math.max(0d, Math.min(1d, Math.max(confidence,
                relation.getConfidence() == null ? 0d : relation.getConfidence().doubleValue())))));
        relation.setReason(merge(relation.getReason(), reason));
        relation.setEvidenceJson(merge(relation.getEvidenceJson(), evidenceJson));
        mapper.updateById(relation);
        return relation;
    }

    private static String merge(String oldValue, String newValue) {
        if (newValue == null || newValue.isBlank()) return oldValue;
        if (oldValue == null || oldValue.isBlank() || oldValue.contains(newValue)) return oldValue == null ? newValue : oldValue;
        return oldValue + "\n" + newValue;
    }

    public List<RagRelation> involving(long repositoryId, RagEnums.ItemType type, String number) {
        return mapper.selectList(Wrappers.<RagRelation>lambdaQuery().eq(RagRelation::getRepositoryId, repositoryId)
                .and(w -> w.and(x -> x.eq(RagRelation::getSourceType, type).eq(RagRelation::getSourceNumber, number))
                        .or(x -> x.eq(RagRelation::getTargetType, type).eq(RagRelation::getTargetNumber, number)))
                .orderByDesc(RagRelation::getUpdatedAt));
    }

    public static Pair canonical(RagEnums.ItemType a, String an, RagEnums.ItemType b, String bn) {
        String left = a.name() + ":" + an; String right = b.name() + ":" + bn;
        return left.compareTo(right) <= 0 ? new Pair(a, an, b, bn) : new Pair(b, bn, a, an);
    }
    public record Pair(RagEnums.ItemType sourceType, String sourceNumber, RagEnums.ItemType targetType, String targetNumber) {}
}
