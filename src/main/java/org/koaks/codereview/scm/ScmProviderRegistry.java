package org.koaks.codereview.scm;

import org.koaks.codereview.common.exception.BizException;
import org.koaks.codereview.repo.domain.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ScmProviderRegistry {

    private final Map<SourceType, ScmProvider> providers = new EnumMap<>(SourceType.class);

    public ScmProviderRegistry(List<ScmProvider> providers) {
        providers.forEach(p -> this.providers.put(p.sourceType(), p));
    }

    public ScmProvider get(SourceType type) {
        ScmProvider provider = providers.get(type);
        if (provider == null) {
            throw BizException.badRequest("source type " + type + " is not supported yet");
        }
        return provider;
    }

}
